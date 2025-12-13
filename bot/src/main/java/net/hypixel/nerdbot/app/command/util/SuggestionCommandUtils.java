package net.hypixel.nerdbot.app.command.util;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.hypixel.nerdbot.app.command.SuggestionStats;
import net.hypixel.nerdbot.core.DiscordTimestamp;
import net.hypixel.nerdbot.discord.cache.EmojiCache;
import net.hypixel.nerdbot.discord.cache.suggestion.Suggestion;
import net.hypixel.nerdbot.discord.config.EmojiConfig;
import net.hypixel.nerdbot.discord.util.DiscordBotEnvironment;
import net.hypixel.nerdbot.discord.util.DiscordUtils;
import net.hypixel.nerdbot.discord.util.pagination.PaginatedResponse;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SuggestionCommandUtils {

    private static final Logger log = LoggerFactory.getLogger(SuggestionCommandUtils.class);

    private SuggestionCommandUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static List<Suggestion> getSuggestions(Member member, @Nullable Long userId, String tags, String title, Suggestion.ChannelType channelType) {
        final List<String> searchTags = (tags == null || tags.trim().isEmpty())
            ? Collections.emptyList()
            : Arrays.asList(tags.split(", ?"));

        List<Suggestion> allSuggestions = DiscordBotEnvironment.getBot().getSuggestionCache().getSuggestions();
        if (allSuggestions.isEmpty()) {
            log.warn("Suggestions cache is empty!");
            return Collections.emptyList();
        }

        return allSuggestions.stream()
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
            .filter(suggestion -> title == null || title.isEmpty() || suggestion.getThreadName()
                .toLowerCase()
                .contains(title.toLowerCase())
            )
            .toList();
    }

    public static SuggestionStats buildSuggestionStats(List<Suggestion> suggestions, @Nullable Member viewer) {
        List<Suggestion> visibleSuggestions = suggestions.stream()
            .filter(suggestion -> viewer == null || suggestion.canSee(viewer))
            .toList();

        int greenlit = (int) visibleSuggestions.stream().filter(Suggestion::isGreenlit).count();
        int agrees = visibleSuggestions.stream().mapToInt(Suggestion::getAgrees).sum();
        int disagrees = visibleSuggestions.stream().mapToInt(Suggestion::getDisagrees).sum();

        return new SuggestionStats(visibleSuggestions.size(), greenlit, agrees, disagrees);
    }

    public static EmbedBuilder buildSuggestionsEmbed(Member member, List<Suggestion> pageItems, SuggestionStats stats, String tags, String title, Suggestion.ChannelType channelType, boolean showNames, boolean showRatio) {
        List<Suggestion> list = pageItems.stream().filter(suggestion -> suggestion.canSee(member)).toList();

        StringJoiner links = new StringJoiner("\n");
        StringBuilder filtersBuilder = new StringBuilder();

        if (tags != null && !tags.isEmpty()) {
            filtersBuilder.append("- Filtered by tags: `").append(tags).append("`\n");
        }

        if (title != null && !title.isEmpty()) {
            filtersBuilder.append("- Filtered by title: `").append(title).append("`\n");
        }

        if (channelType != Suggestion.ChannelType.NORMAL) {
            filtersBuilder.append("- Filtered by type: `").append(channelType.getName()).append("`");
        }

        String filters = filtersBuilder.toString();

        list.forEach(suggestion -> {
            String link = "[" + suggestion.getThreadName().replaceAll("`", "") + "](" + suggestion.getJumpUrl() + ")";
            link += (suggestion.isGreenlit() ? " " + EmojiCache.getFormattedEmoji(EmojiConfig::getGreenlitEmojiId) : "") + "\n";

            if (!suggestion.getAppliedTags().isEmpty()) {
                link += "Tags: `" + suggestion.getAppliedTags().stream().map(ForumTag::getName).collect(Collectors.joining(", ")) + "`\n";

            }

            link += "Created at " + DiscordTimestamp.toLongDateTime(suggestion.getTimeCreated().toEpochSecond() * 1_000);
            link = link.replaceAll("\n\n", "\n");

            if (showNames) {
                User user = DiscordBotEnvironment.getBot().getJDA().getUserById(suggestion.getOwnerIdLong());
                if (user != null) {
                    link += " by " + user.getAsMention();
                }
            }

            link += "\n";
            link += EmojiCache.getFormattedEmoji(EmojiConfig::getAgreeEmojiId) + " " + suggestion.getAgrees() + "\u3000" + EmojiCache.getFormattedEmoji(EmojiConfig::getDisagreeEmojiId) + " " + suggestion.getDisagrees() + "\n";
            links.add(link);
        });

        EmbedBuilder embedBuilder = new EmbedBuilder();
        int statsFields = 1;

        embedBuilder.setColor(Color.GREEN)
            .setTitle("Suggestions")
            .setDescription(links.toString())
            .addField(
                "Total",
                String.valueOf(stats.totalSuggestions()),
                true
            );

        if (showRatio && channelType == Suggestion.ChannelType.NORMAL) {
            statsFields++;
            embedBuilder.addField(
                EmojiCache.getFormattedEmoji(EmojiConfig::getGreenlitEmojiId),
                stats.greenlitSuggestions() + " (" + stats.greenlitPercentage() + "%)",
                true
            );
        }

        if (showRatio) {
            statsFields++;
            embedBuilder.addField(
                "Reactions",
                EmojiCache.getFormattedEmoji(EmojiConfig::getAgreeEmojiId) + " " + stats.totalAgrees() + "\u3000" + EmojiCache.getFormattedEmoji(EmojiConfig::getDisagreeEmojiId) + " " + stats.totalDisagrees() + " (Total: " + stats.totalReactions() + ")",
                true
            );
        }

        int blankFields = Math.max(0, 3 - statsFields);
        for (int i = 0; i < blankFields; i++) {
            embedBuilder.addBlankField(true);
        }

        if (!filters.isEmpty()) {
            embedBuilder.addField("Filters", filters, false);
        }

        long minutesSinceStart = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - DiscordBotEnvironment.getBot().getStartTime());
        if (minutesSinceStart <= 30) {
            embedBuilder.setFooter("The bot recently started so results may be inaccurate!");
        }

        return embedBuilder;
    }

    public static PaginatedResponse<Suggestion> createSuggestionsPagination(
        Member viewer,
        List<Suggestion> suggestions,
        SuggestionStats stats,
        String tags,
        String title,
        Suggestion.ChannelType channelType,
        boolean showNames,
        boolean showRatio,
        String paginationId,
        @Nullable String authorName,
        @Nullable String thumbnailUrl
    ) {
        return PaginatedResponse.forEmbeds(
            suggestions,
            10,
            pageItems -> {
                EmbedBuilder builder = buildSuggestionsEmbed(viewer, pageItems, stats, tags, title, channelType, showNames, showRatio);

                if (authorName != null) {
                    builder.setAuthor(authorName);
                }

                if (thumbnailUrl != null) {
                    builder.setThumbnail(thumbnailUrl);
                }

                return builder.build();
            },
            paginationId
        );
    }
}