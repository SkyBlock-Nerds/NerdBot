package net.hypixel.nerdbot.app.command;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import net.aerh.slashcommands.api.annotations.SlashAutocompleteHandler;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashComponentHandler;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.hypixel.nerdbot.app.command.util.SuggestionCommandUtils;
import net.hypixel.nerdbot.app.curator.ForumChannelCurator;
import net.hypixel.nerdbot.app.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.core.DiscordTimestamp;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.cache.ChannelCache;
import net.hypixel.nerdbot.discord.cache.suggestion.Suggestion;
import net.hypixel.nerdbot.discord.config.EmojiConfig;
import net.hypixel.nerdbot.discord.config.suggestion.SuggestionConfig;
import net.hypixel.nerdbot.discord.storage.database.model.greenlit.GreenlitMessage;
import net.hypixel.nerdbot.discord.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.discord.storage.database.repository.DiscordUserRepository;
import net.hypixel.nerdbot.discord.storage.database.repository.GreenlitMessageRepository;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import net.hypixel.nerdbot.discord.util.pagination.PaginatedResponse;
import net.hypixel.nerdbot.discord.util.pagination.PaginationManager;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SuggestionCommands {

    private static final Logger log = LoggerFactory.getLogger(SuggestionCommands.class);

    private final Cache<@NotNull String, Long> lastReviewRequestCache = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .scheduler(Scheduler.systemScheduler())
        .removalListener((o, o2, removalCause) -> log.info("Removed {} from last review request cache", o))
        .build();


    @SlashCommand(name = "request-review", description = "Request a greenlit review of your suggestion.", guildOnly = true)
    public void requestSuggestionReview(SlashCommandInteractionEvent event) {
        event.deferReply(true).complete();

        if (!DiscordBotEnvironment.getBot().getConfig().getSuggestionConfig().getReviewRequestConfig().isEnabled()) {
            event.getHook().editOriginal("Review requests are currently disabled!").queue();
            return;
        }

        DiscordUserRepository repository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);

        repository.findOrCreateByIdAsync(event.getMember().getId())
            .thenAccept(discordUser -> {
                if (event.getChannel().getType() != net.dv8tion.jda.api.entities.channel.ChannelType.GUILD_PUBLIC_THREAD) {
                    event.getHook().editOriginal("This command cannot be used here!").queue();
                    return;
                }

                processReviewRequest(event, discordUser);
            })
            .exceptionally(throwable -> {
                log.error("Error loading user for review request", throwable);
                event.getHook().editOriginal("Failed to load user data").queue();
                return null;
            });
    }

    private void processReviewRequest(SlashCommandInteractionEvent event, DiscordUser discordUser) {
        SuggestionConfig suggestionConfig = DiscordBotEnvironment.getBot().getConfig().getSuggestionConfig();
        EmojiConfig emojiConfig = DiscordBotEnvironment.getBot().getConfig().getEmojiConfig();
        String forumChannelId = event.getChannel().asThreadChannel().getParentChannel().getId();

        // Handle Non-Suggestion Channels
        if (!forumChannelId.equals(suggestionConfig.getForumChannelId())) {
            event.getHook().editOriginal("You cannot send threads in non-suggestion channels for review!").queue();
            return;
        }

        if (lastReviewRequestCache.getIfPresent(event.getChannel().getId()) != null) {
            event.getHook().editOriginal("You cannot request another review on this thread yet!").queue();
            return;
        }

        Suggestion suggestion = DiscordBotEnvironment.getBot().getSuggestionCache().getSuggestion(event.getChannel().getId());

        // Handle Missing Suggestion
        if (suggestion == null) {
            event.getHook().editOriginal("Couldn't find that suggestion in the cache! Please try again later!").queue();
            return;
        }

        // Handle User Deleted Posts
        java.util.Optional<Message> firstMessage = suggestion.getFirstMessage();
        if (firstMessage.isEmpty()) {
            event.getHook().editOriginal("The original message for this suggestion was deleted!").queue();
            return;
        }

        // No Friends Allowed
        if (firstMessage.get().getAuthor().getIdLong() != event.getUser().getIdLong()) {
            event.getHook().editOriginal("You cannot request a review on a thread you did not create!").queue();
            return;
        }

        // Handle Already Greenlit
        if (suggestion.isGreenlit()) {
            event.getHook().editOriginal("You can only request reviews on threads that are not greenlit!").queue();
            return;
        }

        // Handle Minimum Agrees
        if (suggestion.getAgrees() < suggestionConfig.getReviewRequestConfig().getThreshold()) {
            event.getHook().editOriginal(String.format("You need at least %d agrees to request a review!", suggestionConfig.getReviewRequestConfig().getThreshold())).queue();
            return;
        }

        // Make sure the suggestion is old enough
        if (System.currentTimeMillis() - firstMessage.get().getTimeCreated().toInstant().toEpochMilli() < suggestionConfig.getReviewRequestConfig().getMinimumSuggestionAge()) {
            event.getHook().editOriginal("This suggestion is too new to request a review!").queue();
            return;
        }

        // Handle Greenlit Ratio
        if (suggestionConfig.getReviewRequestConfig().isEnforceGreenlitRatio() && suggestion.getRatio() <= suggestionConfig.getGreenlitRatio()) {
            event.getHook().editOriginal(String.format("You need at least a %d%% agree ratio to request a review!", suggestionConfig.getGreenlitRatio())).queue();
            return;
        }

        // Send Request Review Message
        ChannelCache.getRequestedReviewChannel().ifPresentOrElse(textChannel -> {
            textChannel.sendMessageEmbeds(
                    new EmbedBuilder()
                        .setAuthor(String.format("Greenlit Review Request from %s", event.getUser().getEffectiveName()))
                        .setTitle(
                            suggestion.getThreadName(),
                            firstMessage.map(Message::getJumpUrl)
                                .orElse(suggestion.getJumpUrl())
                        )
                        .setDescription(
                            firstMessage.map(Message::getContentRaw)
                                .orElse("*Main message missing!*")
                        )
                        .setColor(Color.GRAY)
                        .setFooter(String.format(
                            "ID: %s",
                            suggestion.getThreadId()
                        ))
                        .addField(
                            "Agrees",
                            String.valueOf(suggestion.getAgrees()),
                            true
                        )
                        .addField(
                            "Disagrees",
                            String.valueOf(suggestion.getDisagrees()),
                            true
                        )
                        .addField(
                            "Neutrals",
                            String.valueOf(suggestion.getNeutrals()),
                            true
                        )
                        .addField(
                            "Tags",
                            suggestion.getAppliedTags()
                                .stream()
                                .map(ForumTag::getName)
                                .collect(Collectors.joining(", ")),
                            false
                        )
                        .addField(
                            "Created",
                            firstMessage.map(Message::getTimeCreated)
                                .map(date -> DiscordTimestamp.toRelativeTimestamp(date.toInstant().toEpochMilli()))
                                .orElse("???"),
                            false
                        )
                        .build()
                )
                .addActionRow(
                    Button.of(
                        ButtonStyle.SUCCESS,
                        String.format(
                            "suggestion-review-accept-%s",
                            suggestion.getThreadId()
                        ),
                        "Greenlit",
                        java.util.Optional.ofNullable(emojiConfig.getGreenlitEmojiId())
                            .map(StringUtils::stripToNull)
                            .map(emojiId -> Emoji.fromCustom(
                                "greenlit",
                                Long.parseLong(emojiId),
                                false
                            ))
                            .orElse(null)
                    ),
                    Button.of(
                        ButtonStyle.DANGER,
                        String.format(
                            "suggestion-review-deny-%s",
                            suggestion.getThreadId()
                        ),
                        "Deny w/o Locking",
                        java.util.Optional.ofNullable(emojiConfig.getDisagreeEmojiId())
                            .map(StringUtils::stripToNull)
                            .map(emojiId -> Emoji.fromCustom(
                                "disagree",
                                Long.parseLong(emojiId),
                                false
                            ))
                            .orElse(null)
                    ),
                    Button.of(
                        ButtonStyle.SECONDARY,
                        String.format(
                            "suggestion-review-lock-%s",
                            suggestion.getThreadId()
                        ),
                        "Lock",
                        Emoji.fromUnicode("🔒")
                    )
                )
                .queue();
        }, () -> log.warn("Review request channel not found!"));

        lastReviewRequestCache.put(event.getChannel().getId(), System.currentTimeMillis());
        event.getHook().deleteOriginal().complete();
        event.getChannel().sendMessage("This suggestion has been sent for review!").queue();
    }

    @SlashCommand(
        name = "suggestions",
        subcommand = "by-id",
        description = "View user suggestions.",
        guildOnly = true
    )
    public void viewMemberSuggestions(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "User ID to view.") String userId,
        @SlashOption(description = "Tags to filter for (comma separated).", required = false) String tags,
        @SlashOption(description = "Words to filter title for.", required = false) String title,
        @SlashOption(description = "Show suggestions from a specific category.", autocompleteId = "suggestion-types", required = false) String channelType
    ) {
        event.deferReply(true).complete();
        Suggestion.ChannelType channelTypeEnum = (channelType == null || channelType.isEmpty() ? Suggestion.ChannelType.NORMAL : Suggestion.ChannelType.getType(channelType));

        if (channelTypeEnum == Suggestion.ChannelType.UNKNOWN) {
            event.getHook().editOriginal("Invalid channel type provided!").queue();
            return;
        }

        try {
            long userIdLong = Long.parseLong(userId);
            User searchUser = DiscordBotEnvironment.getBot().getJDA().getUserById(userIdLong);
            boolean showRatio = userIdLong == event.getMember().getIdLong() || event.getMember().hasPermission(Permission.MANAGE_PERMISSIONS);
            List<Suggestion> suggestions = SuggestionCommandUtils.getSuggestions(event.getMember(), userIdLong, tags, title, channelTypeEnum);
            SuggestionStats stats = SuggestionCommandUtils.buildSuggestionStats(suggestions, event.getMember());

            if (suggestions.isEmpty()) {
                event.getHook().editOriginal("No suggestions found matching that filter!").queue();
                return;
            }

            PaginatedResponse<Suggestion> pagination = SuggestionCommandUtils.createSuggestionsPagination(
                event.getMember(),
                suggestions,
                stats,
                tags,
                title,
                channelTypeEnum,
                false,
                showRatio,
                "suggestions-page",
                searchUser != null ? searchUser.getName() : userId,
                searchUser != null ? searchUser.getEffectiveAvatarUrl() : null
            );

            pagination.sendMessage(event);
            event.getHook().retrieveOriginal().queue(message ->
                PaginationManager.registerPagination(message.getId(), pagination)
            );
        } catch (NumberFormatException exception) {
            event.getHook().editOriginal("Invalid user ID!").queue();
        }
    }

    @SlashCommand(
        name = "suggestions",
        subcommand = "by-member",
        description = "View user suggestions.",
        guildOnly = true
    )
    public void viewMemberSuggestions(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Member to view.") Member member,
        @SlashOption(description = "Tags to filter for (comma separated).", required = false) String tags,
        @SlashOption(description = "Words to filter title for.", required = false) String title,
        @SlashOption(description = "Show suggestions from a specific category.", autocompleteId = "suggestion-types", required = false) String type
    ) {
        event.deferReply(true).complete();
        Suggestion.ChannelType typeEnum = (type == null || type.isEmpty() ? Suggestion.ChannelType.NORMAL : Suggestion.ChannelType.getType(type));

        if (typeEnum == Suggestion.ChannelType.UNKNOWN) {
            event.getHook().editOriginal("Invalid channel type provided!").queue();
            return;
        }

        boolean showRatio = member.getIdLong() == event.getMember().getIdLong() || event.getMember().hasPermission(Permission.MANAGE_PERMISSIONS);

        List<Suggestion> suggestions = SuggestionCommandUtils.getSuggestions(event.getMember(), member.getIdLong(), tags, title, typeEnum);
        SuggestionStats stats = SuggestionCommandUtils.buildSuggestionStats(suggestions, event.getMember());

        if (suggestions.isEmpty()) {
            event.getHook().editOriginal("No suggestions found matching that filter!").queue();
            return;
        }

        PaginatedResponse<Suggestion> pagination = SuggestionCommandUtils.createSuggestionsPagination(
            member,
            suggestions,
            stats,
            tags,
            title,
            typeEnum,
            false,
            showRatio,
            "suggestions-page",
            member.getEffectiveName(),
            member.getEffectiveAvatarUrl()
        );

        pagination.sendMessage(event);
        event.getHook().retrieveOriginal().queue(message ->
            PaginationManager.registerPagination(message.getId(), pagination)
        );
    }

    @SlashCommand(
        name = "suggestions",
        subcommand = "by-everyone",
        description = "View all suggestions.",
        guildOnly = true
    )
    public void viewAllSuggestions(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Tags to filter for (comma separated).", required = false) String tags,
        @SlashOption(description = "Words to filter title for.", required = false) String title,
        @SlashOption(description = "Show suggestions from a specific category.", autocompleteId = "suggestion-types", required = false) String type
    ) {
        event.deferReply(true).complete();
        Suggestion.ChannelType typeEnum = (type == null || type.isEmpty() ? Suggestion.ChannelType.NORMAL : Suggestion.ChannelType.getType(type));

        if (typeEnum == Suggestion.ChannelType.UNKNOWN) {
            event.getHook().editOriginal("Invalid channel type provided!").queue();
            return;
        }

        List<Suggestion> suggestions = SuggestionCommandUtils.getSuggestions(event.getMember(), null, tags, title, typeEnum);
        SuggestionStats stats = SuggestionCommandUtils.buildSuggestionStats(suggestions, event.getMember());

        if (suggestions.isEmpty()) {
            event.getHook().editOriginal("No suggestions found matching that filter!").queue();
            return;
        }

        PaginatedResponse<Suggestion> pagination = SuggestionCommandUtils.createSuggestionsPagination(
            event.getMember(),
            suggestions,
            stats,
            tags,
            title,
            typeEnum,
            true,
            true,
            "suggestions-page",
            null,
            null
        );

        pagination.sendMessage(event);
        event.getHook().retrieveOriginal().queue(message ->
            PaginationManager.registerPagination(message.getId(), pagination)
        );
    }

    @SlashAutocompleteHandler(id = "suggestion-types")
    public List<Command.Choice> getSuggestionTypes(CommandAutoCompleteInteractionEvent event) {
        return Arrays.stream(Suggestion.ChannelType.VALUES)
            .map(type -> new Command.Choice(type.getName(), type.name()))
            .toList();
    }

    @SlashComponentHandler(id = "suggestion-review", patterns = {"suggestion-review-*"})
    public void handleSuggestionReview(ButtonInteractionEvent event) {
        event.deferEdit().queue();

        DiscordUserRepository userRepository = BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        userRepository.findByIdAsync(event.getUser().getId()).thenAccept(user -> {
            if (user == null) return;

            String[] parts = event.getComponentId().split("-");
            String action = parts[2];
            String threadId = parts[3];

            ThreadChannel thread = DiscordBotEnvironment.getBot().getJDA().getThreadChannelById(threadId);
            if (thread == null) {
                event.getHook().editOriginal("Could not find thread with ID " + threadId + "!").queue();
                return;
            }

            SuggestionConfig suggestionConfig = DiscordBotEnvironment.getBot().getConfig().getSuggestionConfig();
            ForumChannel forum = thread.getParentChannel().asForumChannel();

            if (!DiscordUtils.hasTagByName(forum, suggestionConfig.getGreenlitTag())) {
                event.getHook().editOriginal("This thread does not have the greenlit tag!").queue();
                return;
            }

            Suggestion suggestion = DiscordBotEnvironment.getBot().getSuggestionCache().getSuggestion(thread.getId());
            if (suggestion == null) {
                event.getHook().editOriginal("Could not find this suggestion in the cache! Please try again later.").queue();
                return;
            }

            java.util.Optional<Message> firstMessage = suggestion.getFirstMessage();
            if (firstMessage.isEmpty()) {
                event.getHook().editOriginal("Could not find the first message in this thread!").queue();
                return;
            }

            boolean accepted = switch (action) {
                case "accept" -> {
                    if (DiscordUtils.hasTagByName(thread, suggestionConfig.getGreenlitTag()) ||
                        DiscordUtils.hasTagByName(thread, suggestionConfig.getReviewedTag())) {
                        event.getHook().editOriginal("This suggestion has already been greenlit or reviewed!").queue();
                        yield false;
                    }

                    List<ForumTag> tags = new ArrayList<>(thread.getAppliedTags());
                    tags.add(DiscordUtils.getTagByName(forum, suggestionConfig.getGreenlitTag()));

                    applyThreadChanges(thread, tags, suggestionConfig);

                    GreenlitMessage greenlitMessage = ForumChannelCurator.createGreenlitMessage(
                        firstMessage.get(), thread, suggestion.getAgrees(), suggestion.getNeutrals(), suggestion.getDisagrees());
                    BotEnvironment.getBot().getDatabase().getRepositoryManager().getRepository(GreenlitMessageRepository.class).cacheObject(greenlitMessage);
                    DiscordBotEnvironment.getBot().getSuggestionCache().updateSuggestion(thread);

                    thread.sendMessage("This suggestion has been greenlit.").queue();
                    yield true;
                }
                case "deny" -> {
                    thread.sendMessage("Your recent review request has been denied. We recommend you review your suggestion and make any necessary changes before requesting another review. Thank you!").queue();
                    yield false;
                }
                case "lock" -> {
                    thread.getManager().setLocked(true).queue(
                        unused -> {
                            event.getHook().sendMessage("Thread locked!").setEphemeral(true).queue();
                            thread.sendMessage("We have reviewed your recent request and have decided to lock this suggestion. If you believe this to be a mistake or would like more information, please contact us through Mod Mail.").queue();
                        },
                        throwable -> event.getHook().sendMessage("Unable to lock thread: " + throwable.getMessage()).setEphemeral(true).queue()
                    );
                    yield false;
                }
                default -> {
                    event.getHook().sendMessage("Invalid action!").setEphemeral(true).queue();
                    yield false;
                }
            };

            event.getHook().editOriginalComponents(ActionRow.of(
                (accepted ? Button.success("suggestion-review-completed", "Greenlit")
                    : Button.danger("suggestion-review-completed", "Denied")).asDisabled()
            )).queue();

            PrometheusMetrics.REVIEW_REQUEST_STATISTICS.labels(
                firstMessage.get().getId(),
                firstMessage.get().getAuthor().getId(),
                suggestion.getThreadName(),
                action
            ).inc();
        });
    }

    private void applyThreadChanges(ThreadChannel thread, List<ForumTag> tags, SuggestionConfig suggestionConfig) {
        ThreadChannelManager threadManager = thread.getManager();
        boolean wasArchived = thread.isArchived();

        if (wasArchived) {
            threadManager = threadManager.setArchived(false);
        }

        threadManager = threadManager.setAppliedTags(tags);

        if (wasArchived || suggestionConfig.isArchiveOnGreenlit()) {
            threadManager = threadManager.setArchived(true);
        }

        if (suggestionConfig.isLockOnGreenlit()) {
            threadManager = threadManager.setLocked(true);
        }

        threadManager.complete();
    }

    @SlashComponentHandler(id = "suggestions-pagination", patterns = {"suggestions-page:*"})
    public void handleSuggestionsPagination(ButtonInteractionEvent event) {
        try {
            event.deferEdit().queue();

            boolean handled = PaginationManager.handleButtonInteraction(event);

            if (!handled) {
                log.warn("Could not find pagination for message ID: {}", event.getMessageId());
                event.getHook().editOriginal("This pagination has expired. Please run the command again.").queue();
            }
        } catch (Exception e) {
            log.error("Error handling suggestions pagination button interaction", e);
            event.getHook().editOriginal("An error occurred while navigating pages.").queue();
        }
    }
}
