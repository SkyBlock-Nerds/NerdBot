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
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.language.TranslationManager;
import net.hypixel.nerdbot.bot.config.SuggestionConfig;
import net.hypixel.nerdbot.cache.SuggestionCache;
import net.hypixel.nerdbot.curator.ForumChannelCurator;
import net.hypixel.nerdbot.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
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

        DiscordUserRepository userRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser user = userRepository.findById(event.getUser().getId());

        String buttonId = event.getButton().getId();
        boolean accepted = false;

        if (buttonId == null) {
            return;
        }

        if (buttonId.startsWith("suggestion-review")) {
            String[] parts = buttonId.split("-");
            String action = parts[2];
            String threadId = parts[3];

            ThreadChannel thread = NerdBotApp.getBot().getJDA().getThreadChannelById(threadId);
            SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();

            if (thread == null) {
                TranslationManager.getInstance().send(event.getHook().setEphemeral(true), user, "generic.could_not_find", "thread with ID " + threadId);
                return;
            }

            ForumChannel forum = thread.getParentChannel().asForumChannel();

            if (!Util.hasTagByName(forum, suggestionConfig.getGreenlitTag())) {
                TranslationManager.getInstance().send(event.getHook().setEphemeral(true), user, "generic.could_not_find", "the greenlit tag");
                return;
            }

            SuggestionCache.Suggestion suggestion = NerdBotApp.getBot().getSuggestionCache().getSuggestion(thread.getId());

            if (suggestion == null) {
                TranslationManager.getInstance().send(event.getHook().setEphemeral(true), user, "generic.could_not_find", "this suggestion in the cache! Please try again later.");
                return;
            }

            if (suggestion.getFirstMessage().isEmpty()) {
                TranslationManager.getInstance().send(event.getHook().setEphemeral(true), user, "generic.could_not_find", "the first message in this thread");
                return;
            }

            switch (action) {
                case "accept" -> {
                    if (Util.hasTagByName(thread, suggestionConfig.getGreenlitTag()) || Util.hasTagByName(thread, suggestionConfig.getReviewedTag())) {
                        TranslationManager.getInstance().send(event.getHook().setEphemeral(true), user, "curator.already_greenlit");
                        return;
                    }
                    List<ForumTag> tags = new ArrayList<>(thread.getAppliedTags());
                    tags.add(Util.getTagByName(forum, suggestionConfig.getGreenlitTag()));
                    ThreadChannelManager threadManager = getThreadChannelManager(thread, tags, suggestionConfig);

                    // Send Changes
                    threadManager.complete();
                    GreenlitMessage greenlitMessage = ForumChannelCurator.createGreenlitMessage(thread.getParentChannel().asForumChannel(), suggestion.getFirstMessage().get(), thread, suggestion.getAgrees(), suggestion.getNeutrals(), suggestion.getDisagrees());
                    NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(GreenlitMessageRepository.class).cacheObject(greenlitMessage);
                    NerdBotApp.getBot().getSuggestionCache().updateSuggestion(thread); // Update Suggestion
                    thread.sendMessage(TranslationManager.getInstance().translate("commands.request_review.accepted")).queue();
                    accepted = true;
                }
                case "deny" ->
                    thread.sendMessage(TranslationManager.getInstance().translate("commands.request_review.changes_requested")).queue();
                case "lock" ->
                    thread.getManager().setLocked(true).queue(unused -> {
                        event.getHook().sendMessage("Thread locked!").setEphemeral(true).queue();
                        thread.sendMessage(TranslationManager.getInstance().translate("commands.request_review.locked")).queue();
                        }, throwable -> event.getHook().sendMessage("Unable to lock thread!").setEphemeral(true).queue());
                default -> {
                    event.getHook().sendMessage("Invalid action!").setEphemeral(true).queue();
                    action = "n/a";
                }
            }

            event.getHook().editOriginalComponents(ActionRow.of(
                    Button.of(
                        (accepted ? ButtonStyle.SUCCESS : ButtonStyle.DANGER),
                        "suggestion-review-completed",
                        (accepted ? "Greenlit" : "Denied")
                    ).asDisabled()
                ))
                .queue();

            PrometheusMetrics.REVIEW_REQUEST_STATISTICS.labels(suggestion.getFirstMessage().get().getId(), suggestion.getFirstMessage().get().getAuthor().getId(), suggestion.getThreadName(), action).inc();
        }
    }

    @NotNull
    private ThreadChannelManager getThreadChannelManager(ThreadChannel thread, List<ForumTag> tags, SuggestionConfig suggestionConfig) {
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

        return threadManager;
    }

    @SubscribeEvent
    public void onThreadCreateEvent(@NotNull ChannelCreateEvent event) {
        if (isInSuggestionChannel(event)) {
            NerdBotApp.getBot().getSuggestionCache().addSuggestion(event.getChannel().asThreadChannel());
        }
    }

    @SubscribeEvent
    public void onThreadDeleteEvent(@NotNull ChannelDeleteEvent event) {
        if (isInSuggestionChannel(event)) {
            NerdBotApp.getBot().getSuggestionCache().removeSuggestion(event.getChannel().asThreadChannel());
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
