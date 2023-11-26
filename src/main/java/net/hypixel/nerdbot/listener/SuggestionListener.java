package net.hypixel.nerdbot.listener;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.GenericChannelEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.bot.config.SuggestionConfig;
import net.hypixel.nerdbot.cache.SuggestionCache;
import net.hypixel.nerdbot.curator.ForumChannelCurator;
import net.hypixel.nerdbot.repository.GreenlitMessageRepository;
import net.hypixel.nerdbot.util.Util;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class SuggestionListener {

    @SubscribeEvent
    public void onButtonClickEvent(@NotNull ButtonInteractionEvent event) {
        event.deferEdit().queue();
        String buttonId = event.getButton().getId();
        boolean accepted = false;

        if (buttonId == null) {
            return;
        }

        if (buttonId.startsWith("suggestion-review")) {
            String[] parts = buttonId.split("-");
            String action = parts[2];
            String threadId = parts[3];

            if (action.equals("accept")) {
                ThreadChannel thread = NerdBotApp.getBot().getJDA().getThreadChannelById(threadId);
                SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();

                if (thread == null) {
                    event.getHook().sendMessage("Unable to locate thread with ID " + threadId).setEphemeral(true).queue();
                    return;
                }

                ForumChannel forum = thread.getParentChannel().asForumChannel();

                if (!Util.hasTagByName(forum, suggestionConfig.getGreenlitTag())) {
                    event.getHook().sendMessage("Unable to locate greenlit tag.").setEphemeral(true).queue();
                    return;
                }

                SuggestionCache.Suggestion suggestion = NerdBotApp.getSuggestionCache().getSuggestion(thread.getId());

                if (suggestion == null) {
                    event.getHook().sendMessage("Unable to locate suggestion in the cache! Try again later.").complete();
                    return;
                }

                if (suggestion.getFirstMessage().isEmpty()) {
                    event.getHook().sendMessage("Unable to locate first message.").setEphemeral(true).queue();
                    return;
                }

                if (Util.hasTagByName(thread, suggestionConfig.getGreenlitTag()) || Util.hasTagByName(thread, suggestionConfig.getReviewedTag())) {
                    event.getHook().sendMessage("This suggestion is already greenlit!").setEphemeral(true).queue();
                    return;
                }

                List<ForumTag> tags = new ArrayList<>(thread.getAppliedTags());
                tags.add(Util.getTagByName(forum, suggestionConfig.getGreenlitTag()));
                ThreadChannelManager threadManager = thread.getManager();
                boolean wasArchived = thread.isArchived();

                if (wasArchived) {
                    threadManager = threadManager.setArchived(false);
                }

                threadManager = threadManager.setAppliedTags(tags);

                // Handle Archiving and Locking
                if (wasArchived || suggestionConfig.isArchiveOnGreenlit()) {
                    threadManager = threadManager.setArchived(true);
                }

                if (suggestionConfig.isLockOnGreenlit()) {
                    threadManager = threadManager.setLocked(true);
                }

                // Send Changes
                threadManager.queue();

                GreenlitMessage greenlitMessage = ForumChannelCurator.createGreenlitMessage(thread.getParentChannel().asForumChannel(), suggestion.getFirstMessage().get(), thread, suggestion.getAgrees(), suggestion.getNeutrals(), suggestion.getDisagrees());
                NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(GreenlitMessageRepository.class).cacheObject(greenlitMessage);
                NerdBotApp.getSuggestionCache().updateSuggestion(thread); // Update Suggestion
                accepted = true;
            }

            event.getHook().editOriginalComponents(ActionRow.of(
                    Button.of(
                        (accepted ? ButtonStyle.SUCCESS : ButtonStyle.DANGER),
                        "suggestion-review-completed",
                        (accepted ? "Greenlit" : "Denied")
                    ).asDisabled()
                ))
                .queue();
        }
    }

    @SubscribeEvent
    public void onThreadCreateEvent(@NotNull ChannelCreateEvent event) {
        if (isInSuggestionChannel(event)) {
            NerdBotApp.getSuggestionCache().addSuggestion(event.getChannel().asThreadChannel());
        }
    }

    @SubscribeEvent
    public void onThreadDeleteEvent(@NotNull ChannelDeleteEvent event) {
        if (isInSuggestionChannel(event)) {
            NerdBotApp.getSuggestionCache().removeSuggestion(event.getChannel().asThreadChannel());
        }
    }

    private boolean isInSuggestionChannel(GenericChannelEvent event) {
        if (event.getChannelType() == ChannelType.GUILD_PUBLIC_THREAD) {
            SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();
            String forumChannelId = event.getChannel().asThreadChannel().getParentChannel().getId();

            return Util.safeArrayStream(suggestionConfig.getSuggestionForumIds(), suggestionConfig.getAlphaSuggestionForumIds())
                .anyMatch(channelId -> channelId.equals(forumChannelId));
        }

        return false;
    }
}
