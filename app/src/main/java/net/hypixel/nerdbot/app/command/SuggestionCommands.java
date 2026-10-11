package net.hypixel.nerdbot.app.command;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import net.aerh.slashcommands.api.annotations.SlashAutocompleteHandler;
import net.aerh.slashcommands.api.annotations.SlashCommand;
import net.aerh.slashcommands.api.annotations.SlashComponentHandler;
import net.aerh.slashcommands.api.annotations.SlashOption;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.managers.channel.concrete.ThreadChannelManager;
import net.hypixel.nerdbot.app.command.util.CommandReplies;
import net.hypixel.nerdbot.app.command.util.PanelId;
import net.hypixel.nerdbot.app.command.util.SuggestionListings;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.config.ReviewRequestConfig;
import net.hypixel.nerdbot.app.config.SuggestionConfig;
import net.hypixel.nerdbot.app.curator.ForumChannelCurator;
import net.hypixel.nerdbot.app.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.app.storage.GreenlitStore;
import net.hypixel.nerdbot.app.suggestion.Suggestion;
import net.hypixel.nerdbot.app.suggestion.SuggestionCache;
import net.hypixel.nerdbot.app.suggestion.SuggestionFactory;
import net.hypixel.nerdbot.discord.config.EmojiConfig;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import net.hypixel.nerdbot.discord.util.pagination.PaginationManager;
import net.hypixel.nerdbot.marmalade.format.DiscordTimestamp;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
public class SuggestionCommands {

    static final String REVIEW_PREFIX = "suggestion-review";
    /** Review buttons aren't tied to one user, so only {@link PanelId#parse} is used. */
    static final PanelId REVIEW = PanelId.of(REVIEW_PREFIX, 2, 0);
    private static final String PAGINATION_ID = "suggestions-page";
    static final Duration REVIEW_COOLDOWN = Duration.ofHours(1);
    static final String SENT_FOR_REVIEW = "This suggestion has been sent for review!";
    static final String GREENLIT_MESSAGE = "This suggestion has been greenlit.";
    static final String DENIED_MESSAGE = "Your recent review request has been denied. We recommend you review your suggestion and make any necessary changes before requesting another review. Thank you!";
    static final String LOCKED_MESSAGE = "We have reviewed your recent request and have decided to lock this suggestion. If you believe this to be a mistake or would like more information, please contact us through Mod Mail.";

    private final SuggestionCache suggestions;
    private final SuggestionListings listings;
    private final SuggestionFactory factory;
    private final Provider<NerdBotConfig> config;
    private final JDA jda;
    private final GreenlitStore greenlit;
    private final Executor backgroundWork;
    private final Clock clock;
    private final Cache<String, Long> lastReviewRequests = Caffeine.newBuilder()
        .expireAfterWrite(REVIEW_COOLDOWN)
        .build();

    @Inject
    public SuggestionCommands(SuggestionCache suggestions, SuggestionListings listings, SuggestionFactory factory, Provider<NerdBotConfig> config,
                              JDA jda, GreenlitStore greenlit, Executor backgroundWork, Clock clock) {
        this.suggestions = suggestions;
        this.listings = listings;
        this.factory = factory;
        this.config = config;
        this.jda = jda;
        this.greenlit = greenlit;
        this.backgroundWork = backgroundWork;
        this.clock = clock;
    }

    @SlashCommand(name = "request-review", description = "Request a greenlit review of your suggestion.", guildOnly = true)
    public void requestSuggestionReview(SlashCommandInteractionEvent event) {
        requestReview(event, event.getChannel());
    }

    @SlashCommand(name = "suggestions", subcommand = "by-id", description = "View user suggestions.", guildOnly = true)
    public void viewMemberSuggestions(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "User ID to view.") String userId,
        @SlashOption(description = "Tags to filter for (comma separated).", required = false) String tags,
        @SlashOption(description = "Words to filter title for.", required = false) String title,
        @SlashOption(description = "Show suggestions from a specific category.", autocompleteId = "suggestion-types", required = false) String channelType
    ) {
        long ownerId;
        try {
            ownerId = Long.parseLong(userId.trim());
        } catch (NumberFormatException exception) {
            CommandReplies.replyPrivately(event, "Invalid user ID!");
            return;
        }

        User owner = jda.getUserById(ownerId);
        listings.show(event, new SuggestionListings.Request(ownerId,
            owner != null ? owner.getName() : userId.trim(),
            owner != null ? owner.getEffectiveAvatarUrl() : null,
            false, canSeeRatio(event.getMember(), ownerId)), tags, title, channelType, PAGINATION_ID);
    }

    @SlashCommand(name = "suggestions", subcommand = "by-member", description = "View user suggestions.", guildOnly = true)
    public void viewMemberSuggestions(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Member to view.") Member member,
        @SlashOption(description = "Tags to filter for (comma separated).", required = false) String tags,
        @SlashOption(description = "Words to filter title for.", required = false) String title,
        @SlashOption(description = "Show suggestions from a specific category.", autocompleteId = "suggestion-types", required = false) String type
    ) {
        listings.show(event, new SuggestionListings.Request(member.getIdLong(), member.getEffectiveName(), member.getEffectiveAvatarUrl(),
            false, canSeeRatio(event.getMember(), member.getIdLong())), tags, title, type, PAGINATION_ID);
    }

    @SlashCommand(name = "suggestions", subcommand = "by-everyone", description = "View all suggestions.", guildOnly = true)
    public void viewAllSuggestions(
        SlashCommandInteractionEvent event,
        @SlashOption(description = "Tags to filter for (comma separated).", required = false) String tags,
        @SlashOption(description = "Words to filter title for.", required = false) String title,
        @SlashOption(description = "Show suggestions from a specific category.", autocompleteId = "suggestion-types", required = false) String type
    ) {
        listings.show(event, new SuggestionListings.Request(null, null, null, true, true), tags, title, type, PAGINATION_ID);
    }

    @SlashAutocompleteHandler(id = "suggestion-types")
    public List<Command.Choice> getSuggestionTypes(CommandAutoCompleteInteractionEvent event) {
        return Arrays.stream(Suggestion.ChannelType.VALUES)
            .map(type -> new Command.Choice(type.getName(), type.name()))
            .toList();
    }

    @SlashComponentHandler(id = REVIEW_PREFIX, patterns = {REVIEW_PREFIX + "-*"})
    public void handleSuggestionReview(ButtonInteractionEvent event) {
        review(event);
    }

    @SlashComponentHandler(id = "suggestions-pagination", patterns = {PAGINATION_ID + ":*"})
    public void handleSuggestionsPagination(ButtonInteractionEvent event) {
        event.deferEdit().queue();

        if (!PaginationManager.handleButtonInteraction(event)) {
            log.warn("Could not find pagination for message ID: {}", event.getMessageId());
            event.getHook().editOriginal("This pagination has expired. Please run the command again.").queue();
        }
    }

    void requestReview(IReplyCallback interaction, @Nullable MessageChannelUnion channel) {
        InteractionHook hook = CommandReplies.deferPrivately(interaction);
        SuggestionConfig suggestionConfig = config.get().getSuggestionConfig();
        ReviewRequestConfig reviewConfig = suggestionConfig.getReviewRequestConfig();

        // The section is optional in the config, and missing means off
        if (reviewConfig == null || !reviewConfig.isEnabled()) {
            hook.editOriginal("Review requests are currently disabled!").queue();
            return;
        }

        if (channel == null || channel.getType() != ChannelType.GUILD_PUBLIC_THREAD) {
            hook.editOriginal("This command cannot be used here!").queue();
            return;
        }

        ThreadChannel thread = channel.asThreadChannel();
        if (!thread.getParentChannel().getId().equals(suggestionConfig.getForumChannelId())) {
            hook.editOriginal("You cannot send threads in non-suggestion channels for review!").queue();
            return;
        }

        if (lastReviewRequests.getIfPresent(thread.getId()) != null) {
            hook.editOriginal("You cannot request another review on this thread yet!").queue();
            return;
        }

        Optional<Suggestion> suggestion = suggestions.find(thread.getId());
        if (suggestion.isEmpty()) {
            hook.editOriginal("Couldn't find that suggestion in the cache! Please try again later!").queue();
            return;
        }

        long requesterId = interaction.getUser().getIdLong();
        String requesterName = interaction.getUser().getEffectiveName();

        CommandReplies.finish(factory.firstMessage(thread), hook, "check the review request for " + thread.getId(),
            "Couldn't check that suggestion. Please try again later.", firstMessage -> {
                Optional<String> problem = reviewProblem(suggestion.get(), firstMessage, requesterId, suggestionConfig.getGreenlitRatio(), reviewConfig, clock.millis());
                if (problem.isPresent()) {
                    hook.editOriginal(problem.get()).queue();
                    return;
                }

                Optional<TextChannel> reviewChannel = reviewChannel(reviewConfig);
                if (reviewChannel.isEmpty()) {
                    log.warn("A review was requested for {} but the review request channel '{}' doesn't exist", thread.getId(),
                        reviewConfig.getChannelId());
                    hook.editOriginal("Review requests aren't set up right now. Please let a moderator know.").queue();
                    return;
                }

                reviewChannel.get()
                    .sendMessageEmbeds(reviewEmbed(suggestion.get(), firstMessage.get(), thread, requesterName))
                    .addActionRow(reviewButtons(suggestion.get().getThreadId(), config.get().getEmojiConfig()))
                    .queue(sent -> {
                        lastReviewRequests.put(thread.getId(), clock.millis());
                        hook.deleteOriginal().queue();
                        thread.sendMessage(SENT_FOR_REVIEW).queue();
                    }, failure -> CommandReplies.fail(hook, "Couldn't send the review request. Please try again later.", failure));
            });
    }

    /** Why this request can't go to review, checked in the order members are most likely to hit them. */
    static Optional<String> reviewProblem(Suggestion suggestion, Optional<Message> firstMessage, long requesterId,
                                          double greenlitRatio, ReviewRequestConfig reviewConfig, long nowMillis) {
        if (firstMessage.isEmpty()) {
            return Optional.of("The original message for this suggestion was deleted!");
        }

        if (firstMessage.get().getAuthor().getIdLong() != requesterId) {
            return Optional.of("You cannot request a review on a thread you did not create!");
        }

        if (suggestion.isGreenlit()) {
            return Optional.of("You can only request reviews on threads that are not greenlit!");
        }

        int threshold = reviewConfig.getThreshold();
        if (suggestion.getAgrees() < threshold) {
            return Optional.of(String.format("You need at least %d agrees to request a review!", threshold));
        }

        long age = nowMillis - firstMessage.get().getTimeCreated().toInstant().toEpochMilli();
        if (age < reviewConfig.getMinimumSuggestionAge()) {
            return Optional.of("This suggestion is too new to request a review!");
        }

        if (reviewConfig.isEnforceGreenlitRatio() && suggestion.getRatio() <= greenlitRatio) {
            // The ratio is a double in the config, so %d would throw; 75.0 shows as 75 and 72.5 as 72.5
            String ratio = BigDecimal.valueOf(greenlitRatio).stripTrailingZeros().toPlainString();
            return Optional.of("You need at least a " + ratio + "% agree ratio to request a review!");
        }

        return Optional.empty();
    }

    void review(ButtonInteraction interaction) {
        Optional<List<String>> parts = REVIEW.parse(interaction.getComponentId());
        if (parts.isEmpty()) {
            CommandReplies.replyPrivately(interaction, PanelId.OUTDATED);
            return;
        }
        String action = parts.get().get(0);
        String threadId = parts.get().get(1);

        interaction.deferEdit().queue();
        InteractionHook hook = interaction.getHook();

        ThreadChannel thread = threadById(threadId);
        if (thread == null) {
            followUp(hook, "Could not find thread with ID " + threadId + "!");
            return;
        }

        SuggestionConfig suggestionConfig = config.get().getSuggestionConfig();
        ForumChannel forum = thread.getParentChannel().asForumChannel();
        Optional<ForumTag> greenlitTag = DiscordUtils.findTagByName(forum, suggestionConfig.getGreenlitTag());
        if (greenlitTag.isEmpty()) {
            followUp(hook, "The suggestion forum has no tag named `" + suggestionConfig.getGreenlitTag() + "`!");
            return;
        }

        switch (action) {
            case "accept" -> accept(hook, thread, greenlitTag.get(), suggestionConfig);
            case "deny" -> {
                thread.sendMessage(DENIED_MESSAGE).queue();
                markReviewed(hook, false, action);
            }
            case "lock" -> thread.getManager().setLocked(true).queue(locked -> {
                followUp(hook, "Thread locked!");
                thread.sendMessage(LOCKED_MESSAGE).queue();
                markReviewed(hook, false, action);
            }, failure -> {
                CommandReplies.logFailure("Failed to lock suggestion " + threadId + " from a review request", failure);
                followUp(hook, "Unable to lock the thread. Please check my permissions and try again.");
            });
            default -> followUp(hook, "Invalid action!");
        }
    }

    private void accept(InteractionHook hook, ThreadChannel thread, ForumTag greenlitTag, SuggestionConfig suggestionConfig) {
        if (DiscordUtils.hasTagByName(thread, suggestionConfig.getGreenlitTag()) || DiscordUtils.hasTagByName(thread, suggestionConfig.getReviewedTag())) {
            followUp(hook, "This suggestion has already been greenlit or reviewed!");
            return;
        }

        Optional<Suggestion> suggestion = suggestions.find(thread.getId());
        if (suggestion.isEmpty()) {
            followUp(hook, "Could not find this suggestion in the cache! Please try again later.");
            return;
        }

        EmojiConfig emojiConfig = config.get().getEmojiConfig();
        CommandReplies.finishWithFollowUp(factory.firstMessage(thread), hook, "load suggestion " + thread.getId() + " to greenlight it",
            "Couldn't greenlight the suggestion. Please try again.", firstMessage -> {
                if (firstMessage.isEmpty()) {
                    followUp(hook, "Could not find the first message in this thread!");
                    return;
                }

                // Fetching every voter can take a while on a popular suggestion, so it runs off JDA's threads
                CompletableFuture<Void> greenlighting = CompletableFuture.supplyAsync(() -> ForumChannelCurator.createGreenlitMessage(
                        firstMessage.get(), thread, suggestion.get().getAgrees(), suggestion.get().getNeutrals(), suggestion.get().getDisagrees(), emojiConfig),
                        backgroundWork)
                    .thenCompose(greenlitMessage -> applyGreenlit(thread, greenlitTag, suggestionConfig)
                        .thenCompose(applied -> greenlit.save(greenlitMessage)));

                CommandReplies.finishWithFollowUp(greenlighting, hook, "greenlight suggestion " + thread.getId(),
                    "Couldn't greenlight the suggestion. Please try again.", done -> {
                        suggestions.requestUpdate(thread);
                        thread.sendMessage(GREENLIT_MESSAGE).queue();
                        markReviewed(hook, true, "accept");
                    });
            });
    }

    private CompletableFuture<Void> applyGreenlit(ThreadChannel thread, ForumTag greenlitTag, SuggestionConfig suggestionConfig) {
        List<ForumTag> tags = new ArrayList<>(thread.getAppliedTags());
        tags.add(greenlitTag);

        // Discord won't edit an archived thread, so it's unarchived for the change and archived again after
        boolean wasArchived = thread.isArchived();
        ThreadChannelManager manager = thread.getManager();
        if (wasArchived) {
            manager = manager.setArchived(false);
        }
        manager = manager.setAppliedTags(tags);
        if (wasArchived || suggestionConfig.isArchiveOnGreenlit()) {
            manager = manager.setArchived(true);
        }
        if (suggestionConfig.isLockOnGreenlit()) {
            manager = manager.setLocked(true);
        }

        return manager.submit().thenApply(ignored -> null);
    }

    private void markReviewed(InteractionHook hook, boolean greenlit, String action) {
        hook.editOriginalComponents(ActionRow.of(
            (greenlit ? Button.success(REVIEW_PREFIX + "-completed", "Greenlit") : Button.danger(REVIEW_PREFIX + "-completed", "Denied")).asDisabled()
        )).queue();
        PrometheusMetrics.REVIEW_REQUESTS.labels(action).inc();
    }

    private static void followUp(InteractionHook hook, String message) {
        hook.sendMessage(message).setEphemeral(true).queue();
    }

    private static MessageEmbed reviewEmbed(Suggestion suggestion, Message firstMessage, ThreadChannel thread, String requesterName) {
        return new EmbedBuilder()
            .setAuthor(String.format("Greenlit Review Request from %s", requesterName))
            .setTitle(suggestion.getThreadName(), firstMessage.getJumpUrl())
            .setDescription(firstMessage.getContentRaw())
            .setColor(Color.GRAY)
            .setFooter(String.format("ID: %s", suggestion.getThreadId()))
            .addField("Agrees", String.valueOf(suggestion.getAgrees()), true)
            .addField("Disagrees", String.valueOf(suggestion.getDisagrees()), true)
            .addField("Neutrals", String.valueOf(suggestion.getNeutrals()), true)
            .addField("Tags", thread.getAppliedTags().stream().map(ForumTag::getName).collect(Collectors.joining(", ")), false)
            .addField("Created", DiscordTimestamp.toRelativeTimestamp(firstMessage.getTimeCreated().toInstant().toEpochMilli()), false)
            .build();
    }

    static List<Button> reviewButtons(String threadId, EmojiConfig emojiConfig) {
        return List.of(
            Button.of(ButtonStyle.SUCCESS, REVIEW.build("accept", threadId), "Greenlit", customEmoji("greenlit", emojiConfig.getGreenlitEmojiId())),
            Button.of(ButtonStyle.DANGER, REVIEW.build("deny", threadId), "Deny w/o Locking", customEmoji("disagree", emojiConfig.getDisagreeEmojiId())),
            Button.of(ButtonStyle.SECONDARY, REVIEW.build("lock", threadId), "Lock", Emoji.fromUnicode("🔒"))
        );
    }

    /** Null, so the button just has no emoji, when the config has no id or a malformed one. */
    @Nullable
    static Emoji customEmoji(String name, @Nullable String emojiId) {
        if (emojiId == null || emojiId.isBlank()) {
            return null;
        }
        try {
            return Emoji.fromCustom(name, Long.parseLong(emojiId.trim()), false);
        } catch (NumberFormatException malformed) {
            log.warn("Emoji id '{}' for {} in the config is not a valid id", emojiId, name);
            return null;
        }
    }

    private static boolean canSeeRatio(@Nullable Member viewer, long ownerId) {
        return viewer != null && (viewer.getIdLong() == ownerId || viewer.hasPermission(Permission.MANAGE_PERMISSIONS));
    }

    private Optional<TextChannel> reviewChannel(ReviewRequestConfig reviewConfig) {
        String channelId = reviewConfig.getChannelId();
        if (channelId == null || channelId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jda.getTextChannelById(channelId));
        } catch (NumberFormatException malformed) {
            return Optional.empty();
        }
    }

    @Nullable
    private ThreadChannel threadById(String threadId) {
        try {
            return jda.getThreadChannelById(threadId);
        } catch (NumberFormatException malformed) {
            return null;
        }
    }
}
