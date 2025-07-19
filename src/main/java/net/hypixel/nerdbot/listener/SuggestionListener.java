package net.hypixel.nerdbot.listener;

import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.BaseForumTag;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.forums.ForumTagData;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.GenericChannelEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.managers.channel.concrete.ForumChannelManager;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.language.TranslationManager;
import net.hypixel.nerdbot.bot.config.BotConfig;
import net.hypixel.nerdbot.bot.config.channel.AlphaProjectConfig;
import net.hypixel.nerdbot.bot.config.suggestion.SuggestionConfig;
import net.hypixel.nerdbot.cache.suggestion.Suggestion;
import net.hypixel.nerdbot.curator.ForumChannelCurator;
import net.hypixel.nerdbot.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.repository.GreenlitMessageRepository;
import net.hypixel.nerdbot.util.DiscordUtils;
import net.hypixel.nerdbot.util.SuggestionUtils;
import net.hypixel.nerdbot.util.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
                TranslationManager.send(event.getHook().setEphemeral(true), user, "generic.could_not_find", "thread with ID " + threadId);
                return;
            }

            ForumChannel forum = thread.getParentChannel().asForumChannel();

            if (!DiscordUtils.hasTagByName(forum, suggestionConfig.getGreenlitTag())) {
                TranslationManager.send(event.getHook().setEphemeral(true), user, "generic.could_not_find", "the greenlit tag");
                return;
            }

            Suggestion suggestion = NerdBotApp.getBot().getSuggestionCache().getSuggestion(thread.getId());

            if (suggestion == null) {
                TranslationManager.send(event.getHook().setEphemeral(true), user, "generic.could_not_find", "this suggestion in the cache! Please try again later.");
                return;
            }

            Optional<Message> firstMessage = suggestion.getFirstMessage();
            if (firstMessage.isEmpty()) {
                TranslationManager.send(event.getHook().setEphemeral(true), user, "generic.could_not_find", "the first message in this thread");
                return;
            }

            switch (action) {
                case "accept" -> {
                    if (DiscordUtils.hasTagByName(thread, suggestionConfig.getGreenlitTag()) || DiscordUtils.hasTagByName(thread, suggestionConfig.getReviewedTag())) {
                        TranslationManager.send(event.getHook().setEphemeral(true), user, "curator.already_greenlit");
                        return;
                    }
                    List<ForumTag> tags = new ArrayList<>(thread.getAppliedTags());
                    tags.add(DiscordUtils.getTagByName(forum, suggestionConfig.getGreenlitTag()));
                    ThreadChannelManager threadManager = getThreadChannelManager(thread, tags, suggestionConfig);

                    // Send Changes
                    threadManager.complete();
                    GreenlitMessage greenlitMessage = ForumChannelCurator.createGreenlitMessage(firstMessage.get(), thread, suggestion.getAgrees(), suggestion.getNeutrals(), suggestion.getDisagrees());
                    NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(GreenlitMessageRepository.class).cacheObject(greenlitMessage);
                    NerdBotApp.getBot().getSuggestionCache().updateSuggestion(thread); // Update Suggestion
                    thread.sendMessage(TranslationManager.translate("commands.request_review.accepted")).queue();
                    accepted = true;
                }
                case "deny" ->
                    thread.sendMessage(TranslationManager.translate("commands.request_review.changes_requested")).queue();
                case "lock" -> thread.getManager().setLocked(true).queue(unused -> {
                    event.getHook().sendMessage("Thread locked!").setEphemeral(true).queue();
                    thread.sendMessage(TranslationManager.translate("commands.request_review.locked")).queue();
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

            PrometheusMetrics.REVIEW_REQUEST_STATISTICS.labels(firstMessage.get().getId(), firstMessage.get().getAuthor().getId(), suggestion.getThreadName(), action).inc();
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
    public void onChannelCreate(@NotNull ChannelCreateEvent event) {
        updateConfigForumIds(event);

        if (isInSuggestionChannel(event)) {
            NerdBotApp.getBot().getSuggestionCache().addSuggestion(event.getChannel().asThreadChannel());
        }
    }

    @SubscribeEvent
    public void onChannelUpdate(@NotNull ChannelUpdateNameEvent event) {
        updateConfigForumIds(event);
    }

    @SubscribeEvent
    public void onChannelDelete(ChannelDeleteEvent event) {
        if (event.getChannelType() == net.dv8tion.jda.api.entities.channel.ChannelType.FORUM) {
            Suggestion.ChannelType channelType = SuggestionUtils.getForumSuggestionType(event.getChannel().asForumChannel());

            if (channelType == Suggestion.ChannelType.ALPHA || channelType == Suggestion.ChannelType.PROJECT) {
                BotConfig botConfig = NerdBotApp.getBot().getConfig();
                botConfig.getAlphaProjectConfig().updateForumIds(botConfig, channelType == Suggestion.ChannelType.ALPHA, channelType == Suggestion.ChannelType.PROJECT);
                log.info("Removed {} suggestion forum from bot config: {} (ID: {})", channelType.getName(), event.getChannel().getName(), event.getChannel().getId());
            }
        }

        if (isInSuggestionChannel(event)) {
            NerdBotApp.getBot().getSuggestionCache().removeSuggestion(event.getChannel().asThreadChannel());
        }
    }

    private void updateConfigForumIds(GenericChannelEvent event) {
        if (event.getChannelType() == net.dv8tion.jda.api.entities.channel.ChannelType.FORUM) {
            Suggestion.ChannelType channelType = SuggestionUtils.getForumSuggestionType(event.getChannel().asForumChannel());

            if (channelType == Suggestion.ChannelType.ALPHA || channelType == Suggestion.ChannelType.PROJECT) {
                BotConfig botConfig = NerdBotApp.getBot().getConfig();
                AlphaProjectConfig alphaProjectConfig = botConfig.getAlphaProjectConfig();
                alphaProjectConfig.updateForumIds(botConfig, channelType == Suggestion.ChannelType.ALPHA, channelType == Suggestion.ChannelType.PROJECT);

                // Auto-create tags
                if (alphaProjectConfig.isAutoCreateTags()) {
                    ForumChannelManager forumChannelManager = event.getChannel().asForumChannel().getManager();
                    List<BaseForumTag> currentTags = new ArrayList<>(forumChannelManager.getChannel().getAvailableTags());

                    if (currentTags.stream().noneMatch(baseForumTag -> baseForumTag.getName().equalsIgnoreCase(botConfig.getSuggestionConfig().getReviewedTag()))) {
                        currentTags.add(new ForumTagData(botConfig.getSuggestionConfig().getReviewedTag()).setModerated(true));
                        forumChannelManager.setAvailableTags(currentTags).queue();
                    }
                }

                log.info("New {} suggestion forum created and added to bot config: {} (ID: {})", channelType.getName(), event.getChannel().getName(), event.getChannel().getId());
            }
        }
    }

    private boolean isInSuggestionChannel(GenericChannelEvent event) {
        if (event.getChannelType() == net.dv8tion.jda.api.entities.channel.ChannelType.GUILD_PUBLIC_THREAD) {
            SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();
            AlphaProjectConfig alphaProjectConfig = NerdBotApp.getBot().getConfig().getAlphaProjectConfig();
            String forumChannelId = event.getChannel().asThreadChannel().getParentChannel().getId();

            return forumChannelId.equals(suggestionConfig.getForumChannelId())
                || ArrayUtils.safeArrayStream(alphaProjectConfig.getAlphaForumIds()).anyMatch(forumChannelId::equals)
                || ArrayUtils.safeArrayStream(alphaProjectConfig.getProjectForumIds()).anyMatch(forumChannelId::equals);
        }

        return false;
    }
}
