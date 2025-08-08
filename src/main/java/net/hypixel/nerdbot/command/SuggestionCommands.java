package net.hypixel.nerdbot.command;

import com.freya02.botcommands.api.annotations.Optional;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.autocomplete.annotations.AutocompletionHandler;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.language.TranslationManager;
import net.hypixel.nerdbot.bot.config.EmojiConfig;
import net.hypixel.nerdbot.bot.config.suggestion.SuggestionConfig;
import net.hypixel.nerdbot.cache.ChannelCache;
import net.hypixel.nerdbot.cache.EmojiCache;
import net.hypixel.nerdbot.cache.suggestion.Suggestion;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.DiscordUtils;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class SuggestionCommands extends ApplicationCommand {

    private final Cache<String, Long> lastReviewRequestCache = Caffeine.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .scheduler(Scheduler.systemScheduler())
        .removalListener((o, o2, removalCause) -> log.info("Removed {} from last review request cache", o))
        .build();

    /**
     * Get a list of suggestions based on the provided filters.
     *
     * @param member      The member looking for suggestions.
     * @param userId      The user ID to filter by.
     * @param tags        The tags to filter by.
     * @param title       The title to filter by.
     * @param channelType The {@link Suggestion.ChannelType} to filter by.
     *
     * @return A list of suggestions that match the provided filters.
     */
    public static List<Suggestion> getSuggestions(Member member, @Nullable Long userId, String tags, String title, Suggestion.ChannelType channelType) {
        final List<String> searchTags = Arrays.asList(tags != null ? tags.split(", ?") : new String[0]);

        if (NerdBotApp.getBot().getSuggestionCache().getSuggestions().isEmpty()) {
            log.warn("Suggestions cache is empty!");
            return Collections.emptyList();
        }

        return NerdBotApp.getBot().getSuggestionCache()
            .getSuggestions()
            .stream()
            .filter(suggestion -> suggestion.getChannelType() == channelType)
            .filter(Suggestion::notDeleted)
            .filter(suggestion -> {
                if (userId != null) {
                    Member user = DiscordUtils.getMainGuild().retrieveMemberById(userId).complete();
                    return user != null && suggestion.getOwnerIdLong() == userId && suggestion.canSee(member);
                }
                return true;
            })
            .filter(suggestion -> searchTags.isEmpty() || searchTags.stream().allMatch(tag -> suggestion.getAppliedTags()
                .stream()
                .anyMatch(forumTag -> forumTag.getName().equalsIgnoreCase(tag))
            ))
            .filter(suggestion -> title == null || suggestion.getThreadName()
                .toLowerCase()
                .contains(title.toLowerCase())
            )
            .toList();
    }

    public static EmbedBuilder buildSuggestionsEmbed(Member member, List<Suggestion> suggestions, String tags, String title, Suggestion.ChannelType channelType, int pageNum, boolean showNames, boolean showRatio) {
        List<Suggestion> list = suggestions.stream().filter(suggestion -> suggestion.canSee(member)).toList();
        List<Suggestion> pages = InfoCommands.getPage(list, pageNum, 10);
        int totalPages = (int) Math.ceil(suggestions.size() / 10.0);

        StringJoiner links = new StringJoiner("\n");
        double total = list.size();
        double greenlit = suggestions.stream().filter(Suggestion::isGreenlit).count();
        String filters = (tags != null ? "- Filtered by tags: `" + tags + "`\n" : "") + (title != null ? "- Filtered by title: `" + title + "`\n" : "") + (channelType != Suggestion.ChannelType.NORMAL ? "- Filtered by Type: `" + channelType.getName() + "`" : "");

        pages.forEach(suggestion -> {
            String link = "[" + suggestion.getThreadName().replaceAll("`", "") + "](" + suggestion.getJumpUrl() + ")";
            link += (suggestion.isGreenlit() ? " " + getEmojiFormat(EmojiConfig::getGreenlitEmojiId) : "") + "\n";

            if (!suggestion.getAppliedTags().isEmpty()) {
                link += "Tags: `" + suggestion.getAppliedTags().stream().map(ForumTag::getName).collect(Collectors.joining(", ")) + "`\n";

            }

            link += "Created at " + DiscordTimestamp.toLongDateTime(suggestion.getTimeCreated().toEpochSecond() * 1_000);
            link = link.replaceAll("\n\n", "\n");

            if (showNames) {
                User user = NerdBotApp.getBot().getJDA().getUserById(suggestion.getOwnerIdLong());
                if (user != null) {
                    link += " by " + user.getAsMention();
                }
            }

            link += "\n";
            link += getEmojiFormat(EmojiConfig::getAgreeEmojiId) + " " + suggestion.getAgrees() + "\u3000" + getEmojiFormat(EmojiConfig::getDisagreeEmojiId) + " " + suggestion.getDisagrees() + "\n";
            links.add(link);
        });

        EmbedBuilder embedBuilder = new EmbedBuilder();
        int blankFields = 2;

        embedBuilder.setColor(Color.GREEN)
            .setTitle("Suggestions")
            .setDescription(links.toString())
            .addField(
                "Total",
                String.valueOf((int) total),
                true
            );

        if (showRatio && channelType == Suggestion.ChannelType.NORMAL) {
            blankFields--;
            embedBuilder.addField(
                getEmojiFormat(EmojiConfig::getGreenlitEmojiId),
                (int) greenlit + " (" + (int) ((greenlit / total) * 100.0) + "%)",
                true
            );
        }

        for (int i = 0; i < blankFields; i++) {
            embedBuilder.addBlankField(true);
        }

        embedBuilder.setFooter("Page: " + pageNum + "/" + totalPages);

        if (!filters.isEmpty()) {
            embedBuilder.addField("Filters", filters, false);
        }

        long minutesSinceStart = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - NerdBotApp.getBot().getStartTime());
        if (minutesSinceStart <= 30) {
            embedBuilder.setFooter("Page: " + pageNum + "/" + totalPages + " (The bot recently started so results may be inaccurate!)");
        }

        return embedBuilder;
    }

    private static String getEmojiFormat(Function<EmojiConfig, String> emojiIdFunction) {
        return EmojiCache.getEmojiById(emojiIdFunction.apply(NerdBotApp.getBot().getConfig().getEmojiConfig()))
            .orElseGet(() -> Emoji.fromUnicode("❓"))
            .getFormatted();
    }

    @JDASlashCommand(name = "request-review", description = "Request a greenlit review of your suggestion.")
    public void requestSuggestionReview(GuildSlashEvent event) {
        event.deferReply(true).complete();

        if (!NerdBotApp.getBot().getConfig().getSuggestionConfig().getReviewRequestConfig().isEnabled()) {
            TranslationManager.edit(event.getHook(), "commands.request_review.disabled");
            return;
        }

        DiscordUserRepository repository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        
        repository.findOrCreateByIdAsync(event.getMember().getId())
            .thenAccept(discordUser -> {
                if (event.getChannel().getType() != net.dv8tion.jda.api.entities.channel.ChannelType.GUILD_PUBLIC_THREAD) {
                    TranslationManager.edit(event.getHook(), discordUser, "commands.cannot_be_used_here");
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
    
    private void processReviewRequest(GuildSlashEvent event, DiscordUser discordUser) {
        SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();
        EmojiConfig emojiConfig = NerdBotApp.getBot().getConfig().getEmojiConfig();
        String forumChannelId = event.getChannel().asThreadChannel().getParentChannel().getId();

        // Handle Non-Suggestion Channels
        if (!forumChannelId.equals(suggestionConfig.getForumChannelId())) {
            TranslationManager.edit(event.getHook(), discordUser, "commands.request_review.not_suggestion_channel");
            return;
        }

        if (lastReviewRequestCache.getIfPresent(event.getChannel().getId()) != null) {
            TranslationManager.edit(event.getHook(), discordUser, "commands.request_review.too_soon");
            return;
        }

        Suggestion suggestion = NerdBotApp.getBot().getSuggestionCache().getSuggestion(event.getChannel().getId());

        // Handle Missing Suggestion
        if (suggestion == null) {
            TranslationManager.edit(event.getHook(), discordUser, "cache.suggestions.not_found");
            return;
        }

        // Handle User Deleted Posts
        java.util.Optional<Message> firstMessage = suggestion.getFirstMessage();
        if (firstMessage.isEmpty()) {
            TranslationManager.edit(event.getHook(), discordUser, "cache.suggestions.user_deleted_post");
            return;
        }

        // No Friends Allowed
        if (firstMessage.get().getAuthor().getIdLong() != event.getUser().getIdLong()) {
            TranslationManager.edit(event.getHook(), discordUser, "commands.request_review.not_own_thread");
            return;
        }

        // Handle Already Greenlit
        if (suggestion.isGreenlit()) {
            TranslationManager.edit(event.getHook(), discordUser, "commands.request_review.already_greenlit");
            return;
        }

        // Handle Minimum Agrees
        if (suggestion.getAgrees() < suggestionConfig.getReviewRequestConfig().getThreshold()) {
            TranslationManager.edit(event.getHook(), discordUser, "commands.request_review.not_enough_reactions", suggestionConfig.getReviewRequestConfig().getThreshold());
            return;
        }

        // Make sure the suggestion is old enough
        if (System.currentTimeMillis() - firstMessage.get().getTimeCreated().toInstant().toEpochMilli() < suggestionConfig.getReviewRequestConfig().getMinimumSuggestionAge()) {
            TranslationManager.edit(event.getHook(), discordUser, "commands.request_review.too_new");
            return;
        }

        // Handle Greenlit Ratio
        if (suggestionConfig.getReviewRequestConfig().isEnforceGreenlitRatio() && suggestion.getRatio() <= suggestionConfig.getGreenlitRatio()) {
            TranslationManager.edit(event.getHook(), discordUser, "commands.request_review.bad_reaction_ratio", suggestionConfig.getGreenlitRatio());
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
        event.getChannel().sendMessage(TranslationManager.translate(discordUser, "commands.request_review.success")).queue();
    }

    @JDASlashCommand(
        name = "suggestions",
        subcommand = "by-id",
        description = "View user suggestions."
    )
    public void viewMemberSuggestions(
        GuildSlashEvent event,
        @AppOption(description = "User ID to view.") String userId,
        @AppOption @Optional Integer page,
        @AppOption(description = "Tags to filter for (comma separated).") @Optional String tags,
        @AppOption(description = "Words to filter title for.") @Optional String title,
        @AppOption(description = "Show suggestions from a specific category.", autocomplete = "suggestion-types") @Optional Suggestion.ChannelType channelType
    ) {
        event.deferReply(true).complete();
        page = (page == null) ? 1 : page;
        int pageNum = Math.max(page, 1);
        channelType = (channelType == null ? Suggestion.ChannelType.NORMAL : channelType);

        try {
            long userIdLong = Long.parseLong(userId);
            User searchUser = NerdBotApp.getBot().getJDA().getUserById(userIdLong);
            boolean showRatio = userIdLong == event.getMember().getIdLong() || event.getMember().hasPermission(Permission.MANAGE_PERMISSIONS);
            List<Suggestion> suggestions = getSuggestions(event.getMember(), userIdLong, tags, title, channelType);

            if (suggestions.isEmpty()) {
                TranslationManager.edit(event.getHook(), "cache.suggestions.filtered_none_found");
                return;
            }

            event.getHook().editOriginalEmbeds(
                buildSuggestionsEmbed(event.getMember(), suggestions, tags, title, channelType, pageNum, false, showRatio)
                    .setAuthor(searchUser != null ? searchUser.getName() : userId)
                    .setThumbnail(searchUser != null ? searchUser.getEffectiveAvatarUrl() : null)
                    .build()
            ).queue();
        } catch (NumberFormatException exception) {
            TranslationManager.edit(event.getHook(), "commands.invalid_user_id");
        }
    }

    @JDASlashCommand(
        name = "suggestions",
        subcommand = "by-member",
        description = "View user suggestions."
    )
    public void viewMemberSuggestions(
        GuildSlashEvent event,
        @AppOption(description = "Member to view.") Member member,
        @AppOption @Optional Integer page,
        @AppOption(description = "Tags to filter for (comma separated).") @Optional String tags,
        @AppOption(description = "Words to filter title for.") @Optional String title,
        @AppOption(description = "Show suggestions from a specific category.", autocomplete = "suggestion-types") @Optional Suggestion.ChannelType type
    ) {
        event.deferReply(true).complete();
        page = (page == null) ? 1 : page;
        final int pageNum = Math.max(page, 1);
        type = (type == null ? Suggestion.ChannelType.NORMAL : type);
        boolean showRatio = member.getIdLong() == event.getMember().getIdLong() || event.getMember().hasPermission(Permission.MANAGE_PERMISSIONS);

        List<Suggestion> suggestions = getSuggestions(event.getMember(), member.getIdLong(), tags, title, type);

        if (suggestions.isEmpty()) {
            TranslationManager.edit(event.getHook(), "cache.suggestions.filtered_none_found");
            return;
        }

        event.getHook().editOriginalEmbeds(
            buildSuggestionsEmbed(member, suggestions, tags, title, type, pageNum, false, showRatio)
                .setAuthor(member.getEffectiveName())
                .setThumbnail(member.getEffectiveAvatarUrl())
                .build()
        ).queue();
    }

    @JDASlashCommand(
        name = "suggestions",
        subcommand = "by-everyone",
        description = "View all suggestions."
    )
    public void viewAllSuggestions(
        GuildSlashEvent event,
        @AppOption @Optional Integer page,
        @AppOption(description = "Tags to filter for (comma separated).") @Optional String tags,
        @AppOption(description = "Words to filter title for.") @Optional String title,
        @AppOption(description = "Show suggestions from a specific category.", autocomplete = "suggestion-types") @Optional Suggestion.ChannelType type
    ) {
        event.deferReply(true).complete();
        page = (page == null) ? 1 : page;
        final int pageNum = Math.max(page, 1);
        type = (type == null ? Suggestion.ChannelType.NORMAL : type);

        List<Suggestion> suggestions = getSuggestions(event.getMember(), null, tags, title, type);

        if (suggestions.isEmpty()) {
            TranslationManager.edit(event.getHook(), "cache.suggestions.filtered_none_found");
            return;
        }

        event.getHook().editOriginalEmbeds(buildSuggestionsEmbed(event.getMember(), suggestions, tags, title, type, pageNum, true, true).build()).queue();
    }

    @AutocompletionHandler(name = "suggestion-types")
    public List<Suggestion.ChannelType> getSuggestionTypes(CommandAutoCompleteInteractionEvent event) {
        return List.of(Suggestion.ChannelType.VALUES);
    }
}
