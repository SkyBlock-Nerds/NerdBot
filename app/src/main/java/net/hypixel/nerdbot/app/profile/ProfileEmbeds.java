package net.hypixel.nerdbot.app.profile;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.app.badge.BadgeCatalog;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.config.RoleConfig;
import net.hypixel.nerdbot.discord.config.objects.RoleRestrictedChannelGroup;
import net.hypixel.nerdbot.marmalade.discord.EmbedFactory;
import net.hypixel.nerdbot.marmalade.storage.badge.Badge;
import net.hypixel.nerdbot.marmalade.storage.badge.TieredBadge;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.badge.BadgeEntry;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.LastActivity;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
import org.apache.commons.lang.time.DateFormatUtils;

import java.awt.Color;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

/** The activity and badge embeds shown by /profile and the moderator /user commands. */
public class ProfileEmbeds {

    private static final String NO_BADGES_IMAGE = "https://i.imgur.com/fs71kmJ.png";

    private static final Color[] RESTRICTED_CHANNEL_COLORS = {
        Color.CYAN,
        Color.MAGENTA,
        Color.PINK,
        Color.BLUE,
        new Color(128, 0, 128), // Purple
        new Color(255, 165, 0), // Orange
        new Color(75, 0, 130),  // Indigo
        new Color(255, 20, 147) // Deep Pink
    };

    /** The project embed has always worded its history fields differently from the other two. */
    private record HistoryLabels(String created, String voted, String commented) {
    }

    private static final HistoryLabels HISTORY_LABELS = new HistoryLabels("Create History", "Vote History", "Comment History");
    private static final HistoryLabels PROJECT_HISTORY_LABELS = new HistoryLabels("Created History", "Voted History", "Commented History");

    /** One kind of suggestion and where its history lives on {@link LastActivity}. */
    private record SuggestionHistory(String title, Color color, HistoryLabels labels,
                                     Function<LastActivity, List<Long>> created,
                                     Function<LastActivity, Map<String, Long>> votes,
                                     Function<LastActivity, List<Long>> comments) {
    }

    private static final List<SuggestionHistory> SUGGESTION_HISTORIES = List.of(
        new SuggestionHistory("Suggestion Activity", Color.YELLOW, HISTORY_LABELS,
            LastActivity::getSuggestionCreationHistory, LastActivity::getSuggestionVoteHistoryMap, LastActivity::getSuggestionCommentHistory),
        new SuggestionHistory("Project Suggestion Activity", Color.ORANGE, PROJECT_HISTORY_LABELS,
            LastActivity::getProjectSuggestionCreationHistory, LastActivity::getProjectSuggestionVoteHistoryMap, LastActivity::getProjectSuggestionCommentHistory),
        new SuggestionHistory("Alpha Suggestion Activity", Color.RED, HISTORY_LABELS,
            LastActivity::getAlphaSuggestionCreationHistory, LastActivity::getAlphaSuggestionVoteHistoryMap, LastActivity::getAlphaSuggestionCommentHistory)
    );

    private final Provider<NerdBotConfig> config;
    private final BadgeCatalog badges;

    @Inject
    public ProfileEmbeds(Provider<NerdBotConfig> config, BadgeCatalog badges) {
        this.config = config;
        this.badges = badges;
    }

    public List<MessageEmbed> activity(Member member, DiscordUser user) {
        // Records written before activity tracking have none stored
        LastActivity lastActivity = user.getLastActivity() == null ? new LastActivity() : user.getLastActivity();
        RoleConfig roleConfig = config.get().getRoleConfig();
        List<MessageEmbed> embeds = new ArrayList<>();

        embeds.add(EmbedFactory.success("General Activity", null)
            .addField("Last Seen", lastActivity.toRelativeTimestamp(LastActivity::getLastGlobalActivity), true)
            .addField("General Voice Chat", lastActivity.toRelativeTimestamp(LastActivity::getLastVoiceChannelJoinDate), true)
            .addField("Item Generator", lastActivity.toRelativeTimestamp(LastActivity::getLastItemGenUsage), true)
            .addField("Projects", lastActivity.toRelativeTimestamp(LastActivity::getLastProjectActivity), true)
            .addField("Project Voice Chat", lastActivity.toRelativeTimestamp(LastActivity::getProjectVoiceJoinDate), true)
            .addBlankField(true)
            .addField("Alpha", lastActivity.toRelativeTimestamp(LastActivity::getLastAlphaActivity), true)
            .addField("Alpha Voice Chat", lastActivity.toRelativeTimestamp(LastActivity::getAlphaVoiceJoinDate), true)
            .addBlankField(true)
            .setFooter(String.format("Activity Requirements (per %d days): %d messages, %d votes, %d comments",
                roleConfig.getDaysRequiredForInactivityCheck(), roleConfig.getMessagesRequiredForInactivityCheck(),
                roleConfig.getVotesRequiredForInactivityCheck(), roleConfig.getCommentsRequiredForInactivityCheck()))
            .build());

        String promotionFooter = String.format("Promotion Requirements (per %d days): %d votes, %d comments",
            roleConfig.getDaysRequiredForVoteHistory(), roleConfig.getMinimumVotesRequiredForPromotion(),
            roleConfig.getMinimumCommentsRequiredForPromotion());
        for (SuggestionHistory history : SUGGESTION_HISTORIES) {
            embeds.add(suggestionActivity(lastActivity, history, promotionFooter));
        }

        int colorIndex = 0;
        for (RoleRestrictedChannelGroup group : config.get().getChannelConfig().getRoleRestrictedChannelGroups()) {
            if (!canAccess(member, group)) {
                continue;
            }

            String groupId = group.getIdentifier();
            embeds.add(new EmbedBuilder()
                .setColor(RESTRICTED_CHANNEL_COLORS[colorIndex++ % RESTRICTED_CHANNEL_COLORS.length])
                .setTitle(group.getDisplayName() + " Activity")
                .addField("Last Activity", lastActivity.getRoleRestrictedChannelRelativeTimestamp(groupId), true)
                .addBlankField(true)
                .addBlankField(true)
                .addField("Message History", periodTotals(days -> lastActivity.getRoleRestrictedChannelMessageCount(groupId, days)), true)
                .addField("Vote History", periodTotals(days -> lastActivity.getRoleRestrictedChannelVoteCount(groupId, days)), true)
                .addField("Comment History", periodTotals(days -> lastActivity.getRoleRestrictedChannelCommentCount(groupId, days)), true)
                .setFooter(String.format("Activity Requirements (per %d days): %d messages, %d votes, %d comments",
                    group.getActivityCheckDays(), group.getMinimumMessagesForActivity(),
                    group.getMinimumVotesForActivity(), group.getMinimumCommentsForActivity()))
                .build());
        }

        return embeds;
    }

    private static MessageEmbed suggestionActivity(LastActivity lastActivity, SuggestionHistory history, String footer) {
        return new EmbedBuilder().setColor(history.color())
            .setTitle(history.title())
            .addField("Last Created", lastActivity.toRelativeTimestampList(history.created()), true)
            .addField("Last Voted", lastActivity.toRelativeTimestampMap(history.votes()), true)
            .addField("Last Commented", lastActivity.toRelativeTimestampList(history.comments()), true)
            .addField(history.labels().created(), periodTotals(days -> lastActivity.toTotalPeriodList(history.created(), Duration.ofDays(days))), true)
            .addField(history.labels().voted(), periodTotals(days -> lastActivity.toTotalPeriodMap(history.votes(), Duration.ofDays(days))), true)
            .addField(history.labels().commented(), periodTotals(days -> lastActivity.toTotalPeriodList(history.comments(), Duration.ofDays(days))), true)
            .setFooter(footer)
            .build();
    }

    static String periodTotals(IntFunction<Object> totalOverDays) {
        return "24 Hours: " + totalOverDays.apply(1) + "\n7 Days: " + totalOverDays.apply(7) + "\n30 Days: " + totalOverDays.apply(30);
    }

    private static boolean canAccess(Member member, RoleRestrictedChannelGroup group) {
        return Arrays.stream(group.getRequiredRoleIds())
            .anyMatch(roleId -> member.getRoles().stream().map(Role::getId).anyMatch(roleId::equalsIgnoreCase));
    }

    public MessageEmbed badges(Member member, DiscordUser user, boolean viewingSelf) {
        List<BadgeEntry> entries = badgesOf(user).stream().sorted(badgeDisplayOrder()).toList();
        String name = member.getEffectiveName();

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(viewingSelf ? "Your Badges" : (name.endsWith("s") ? name + "'" : name + "'s") + " Badges")
            .setColor(Color.PINK)
            .setDescription(entries.isEmpty() ? "No badges :(" : String.join("\n\n", entries.stream().map(this::formatBadgeEntry).toList()));

        if (entries.isEmpty()) {
            embed.setImage(NO_BADGES_IMAGE);
        }

        return embed.build();
    }

    public static String skyCryptLink(MojangProfile profile) {
        return String.format("[%s](https://sky.shiiyu.moe/stats/%s)", profile.getUniqueId(), profile.getUniqueId());
    }

    public static String linkedProfile(DiscordUser user) {
        return user.isProfileAssigned()
            ? user.getMojangProfile().getUsername() + " (" + user.getMojangProfile().getUniqueId() + ")"
            : "*Missing Data*";
    }

    public static String badgeCount(DiscordUser user) {
        List<BadgeEntry> entries = badgesOf(user);
        return entries.isEmpty() ? "None" : String.valueOf(entries.size());
    }

    public static String birthday(DiscordUser user) {
        return user.getBirthdayData() == null ? "Not Set" : user.getBirthdayData().getFormattedDisplay();
    }

    private static List<BadgeEntry> badgesOf(DiscordUser user) {
        return user.getBadges() == null ? List.of() : user.getBadges();
    }

    /**
     * Tiered badges first, higher tiers before lower and a tiered entry with a tier before one
     * without; then most recently obtained first.
     */
    Comparator<BadgeEntry> badgeDisplayOrder() {
        return (first, second) -> {
            boolean firstTiered = isTiered(first.badgeId());
            boolean secondTiered = isTiered(second.badgeId());

            if (firstTiered && secondTiered) {
                if (first.tier() != null && second.tier() != null) {
                    int byTier = Integer.compare(second.tier(), first.tier());
                    if (byTier != 0) {
                        return byTier;
                    }
                } else if (first.tier() != null) {
                    return -1;
                } else if (second.tier() != null) {
                    return 1;
                }
            } else if (firstTiered) {
                return -1;
            } else if (secondTiered) {
                return 1;
            }

            return Long.compare(second.obtainedAt(), first.obtainedAt());
        };
    }

    private boolean isTiered(String badgeId) {
        return badges.find(badgeId).filter(TieredBadge.class::isInstance).isPresent();
    }

    String formatBadgeEntry(BadgeEntry entry) {
        Badge badge = badges.find(entry.badgeId()).orElse(null);
        String name;

        // Reconciliation prunes unknown badge ids, but a badge removed from config can still be shown before it runs
        if (badge == null) {
            name = entry.badgeId();
        } else if (badge instanceof TieredBadge tieredBadge && entry.tier() != null && entry.tier() >= 1) {
            name = tieredBadge.getFormattedName(entry.tier());
        } else {
            name = badge.getFormattedName();
        }

        return "**" + name + "**\nObtained on " + DateFormatUtils.format(entry.obtainedAt(), "MMMM dd, yyyy");
    }
}
