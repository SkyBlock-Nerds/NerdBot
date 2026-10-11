package net.hypixel.nerdbot.app.command.util;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.hypixel.nerdbot.app.command.SuggestionStats;
import net.hypixel.nerdbot.app.suggestion.Suggestion;
import net.hypixel.nerdbot.discord.cache.EmojiCache;
import net.hypixel.nerdbot.discord.config.EmojiConfig;
import net.hypixel.nerdbot.discord.util.StringUtils;
import net.hypixel.nerdbot.discord.util.pagination.PaginatedResponse;
import net.hypixel.nerdbot.marmalade.format.DiscordTimestamp;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@UtilityClass
public class SuggestionCommandUtils {

    static final int SUGGESTIONS_PER_PAGE = 10;

    /**
     * The include and exclude tag names parsed from a suggestion tag filter string.
     *
     * @param include tags a suggestion must all have
     * @param exclude tags a suggestion must not have
     */
    public record TagFilters(List<String> include, List<String> exclude) {
    }

    /**
     * What a suggestions page shows and who it's for.
     *
     * @param viewer           whose channel access decides which suggestions are listed
     * @param showNames        whether each line says who posted it
     * @param showRatio        whether the greenlit share and reaction totals are shown
     * @param authorName       the embed's author line, or null for none
     * @param thumbnailUrl     the embed's thumbnail, or null for none
     * @param incomplete       whether the cache is still loading, so results may be missing
     * @param jda              for the cached threads and users the page refers to
     */
    public record Listing(Member viewer, @Nullable String tags, @Nullable String title, Suggestion.ChannelType channelType,
                          boolean showNames, boolean showRatio, @Nullable String authorName, @Nullable String thumbnailUrl,
                          boolean incomplete, JDA jda) {
    }

    /**
     * Split a comma-separated tag filter string into include and exclude lists. Tags prefixed with
     * {@code !} are exclusions (with the {@code !} stripped); the rest are inclusions. A null or
     * blank string yields two empty lists.
     *
     * @param tags the raw tag filter string, e.g. {@code "bug, !wontfix"}
     * @return the parsed include/exclude tag lists
     */
    static TagFilters parseTagFilters(String tags) {
        List<String> allTags = (tags == null || tags.trim().isEmpty())
            ? Collections.emptyList()
            : Arrays.asList(tags.split(", ?"));

        List<String> include = allTags.stream()
            .filter(tag -> !tag.startsWith("!"))
            .toList();
        List<String> exclude = allTags.stream()
            .filter(tag -> tag.startsWith("!"))
            .map(tag -> tag.substring(1))
            .toList();

        return new TagFilters(include, exclude);
    }

    /**
     * The suggestions matching the filters, keeping the order given.
     *
     * @param ownerId only suggestions by this user, and only ones the viewer can see; null for everyone's
     */
    public static List<Suggestion> filterSuggestions(JDA jda, List<Suggestion> all, Member viewer, @Nullable Long ownerId,
                                                     @Nullable String tags, @Nullable String title, Suggestion.ChannelType channelType) {
        TagFilters tagFilters = parseTagFilters(tags);

        return all.stream()
            .filter(suggestion -> suggestion.getChannelType() == channelType)
            .filter(Suggestion::notDeleted)
            .filter(suggestion -> ownerId == null || (suggestion.getOwnerIdLong() == ownerId && suggestion.canSee(jda, viewer)))
            .filter(suggestion -> tagFilters.include().isEmpty() || tagFilters.include().stream().allMatch(tag -> hasTag(jda, suggestion, tag)))
            .filter(suggestion -> tagFilters.exclude().isEmpty() || tagFilters.exclude().stream().noneMatch(tag -> hasTag(jda, suggestion, tag)))
            .filter(suggestion -> title == null || title.isEmpty()
                || suggestion.getThreadName().toLowerCase(Locale.ROOT).contains(title.toLowerCase(Locale.ROOT)))
            .toList();
    }

    public static SuggestionStats buildSuggestionStats(JDA jda, List<Suggestion> suggestions, @Nullable Member viewer) {
        List<Suggestion> visibleSuggestions = suggestions.stream()
            .filter(suggestion -> viewer == null || suggestion.canSee(jda, viewer))
            .toList();

        int greenlit = (int) visibleSuggestions.stream().filter(Suggestion::isGreenlit).count();
        int agrees = visibleSuggestions.stream().mapToInt(Suggestion::getAgrees).sum();
        int disagrees = visibleSuggestions.stream().mapToInt(Suggestion::getDisagrees).sum();

        return new SuggestionStats(visibleSuggestions.size(), greenlit, agrees, disagrees);
    }

    public static EmbedBuilder buildSuggestionsEmbed(List<Suggestion> pageItems, SuggestionStats stats, Listing listing) {
        StringJoiner links = new StringJoiner("\n");

        JDA jda = listing.jda();
        pageItems.stream().filter(suggestion -> suggestion.canSee(jda, listing.viewer())).forEach(suggestion -> {
            String link = "[" + suggestion.getThreadName().replaceAll("`", "") + "](" + suggestion.getJumpUrl() + ")";
            link += (suggestion.isGreenlit() ? " " + EmojiCache.getFormattedEmoji(EmojiConfig::getGreenlitEmojiId) : "") + "\n";

            List<ForumTag> appliedTags = suggestion.getAppliedTags(jda);
            if (!appliedTags.isEmpty()) {
                link += "Tags: `" + appliedTags.stream().map(ForumTag::getName).collect(Collectors.joining(", ")) + "`\n";
            }

            link += "Created at " + DiscordTimestamp.toLongDateTime(suggestion.getTimeCreated().toEpochSecond() * 1_000);
            link = link.replaceAll("\n\n", "\n");

            if (listing.showNames()) {
                User user = jda.getUserById(suggestion.getOwnerIdLong());
                if (user != null) {
                    link += " by " + user.getAsMention();
                }
            }

            link += "\n";
            link += EmojiCache.getFormattedEmoji(EmojiConfig::getAgreeEmojiId) + " " + StringUtils.formatWithCommas(suggestion.getAgrees())
                + "　"
                + EmojiCache.getFormattedEmoji(EmojiConfig::getDisagreeEmojiId) + " " + StringUtils.formatWithCommas(suggestion.getDisagrees()) + "\n";
            links.add(link);
        });

        EmbedBuilder embedBuilder = new EmbedBuilder()
            .setColor(Color.GREEN)
            .setTitle("Suggestions")
            .setDescription(links.toString())
            .addField("Total", StringUtils.formatWithCommas(stats.totalSuggestions()), true);
        int statsFields = 1;

        if (listing.showRatio() && listing.channelType() == Suggestion.ChannelType.NORMAL) {
            statsFields++;
            embedBuilder.addField(
                EmojiCache.getFormattedEmoji(EmojiConfig::getGreenlitEmojiId),
                StringUtils.formatWithCommas(stats.greenlitSuggestions()) + " (" + stats.greenlitPercentage() + "%)",
                true
            );
        }

        if (listing.showRatio()) {
            statsFields++;
            embedBuilder.addField(
                "Reactions",
                EmojiCache.getFormattedEmoji(EmojiConfig::getAgreeEmojiId) + " " + StringUtils.formatWithCommas(stats.totalAgrees())
                    + "　"
                    + EmojiCache.getFormattedEmoji(EmojiConfig::getDisagreeEmojiId) + " " + StringUtils.formatWithCommas(stats.totalDisagrees())
                    + " (Total: " + StringUtils.formatWithCommas(stats.totalReactions()) + ")",
                true
            );
        }

        for (int i = statsFields; i < 3; i++) {
            embedBuilder.addBlankField(true);
        }

        String filters = filtersText(listing);
        if (!filters.isEmpty()) {
            embedBuilder.addField("Filters", filters, false);
        }

        if (listing.incomplete()) {
            embedBuilder.setFooter("Suggestions are still loading, so results may be incomplete!");
        }

        if (listing.authorName() != null) {
            embedBuilder.setAuthor(listing.authorName());
        }

        if (listing.thumbnailUrl() != null) {
            embedBuilder.setThumbnail(listing.thumbnailUrl());
        }

        return embedBuilder;
    }

    public static PaginatedResponse<Suggestion> createSuggestionsPagination(List<Suggestion> suggestions, SuggestionStats stats, Listing listing, String paginationId) {
        return PaginatedResponse.forEmbeds(suggestions, SUGGESTIONS_PER_PAGE, pageItems -> buildSuggestionsEmbed(pageItems, stats, listing).build(), paginationId);
    }

    static String filtersText(Listing listing) {
        StringBuilder filters = new StringBuilder();

        if (listing.tags() != null && !listing.tags().isEmpty()) {
            filters.append("- Filtered by tags: `").append(listing.tags()).append("`\n");
        }

        if (listing.title() != null && !listing.title().isEmpty()) {
            filters.append("- Filtered by title: `").append(listing.title()).append("`\n");
        }

        if (listing.channelType() != Suggestion.ChannelType.NORMAL) {
            filters.append("- Filtered by type: `").append(listing.channelType().getName()).append("`");
        }

        return filters.toString();
    }

    private static boolean hasTag(JDA jda, Suggestion suggestion, String tag) {
        return suggestion.getAppliedTags(jda).stream().anyMatch(forumTag -> forumTag.getName().equalsIgnoreCase(tag));
    }
}
