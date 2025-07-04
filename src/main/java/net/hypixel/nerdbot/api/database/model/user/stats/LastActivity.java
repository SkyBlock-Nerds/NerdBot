package net.hypixel.nerdbot.api.database.model.user.stats;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;
import org.apache.commons.lang.time.DateFormatUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;

@Getter
@Setter
@Log4j2
public class LastActivity {

    // Global Activity
    private long lastGlobalActivity = -1L;
    private long lastVoiceChannelJoinDate = -1L;
    private long lastItemGenUsage = -1L;
    private long lastModMailUsage = -1L;

    // Suggestion Activity History
    private List<Long> suggestionCreationHistory = new ArrayList<>();
    @Deprecated(forRemoval = true)
    private List<Long> suggestionVoteHistory = new ArrayList<>();
    private List<Long> suggestionCommentHistory = new ArrayList<>();

    // Alpha Activity
    private long lastAlphaActivity = -1L;
    private long alphaVoiceJoinDate = -1L;

    // Alpha Suggestion Activity History
    private List<Long> alphaSuggestionCreationHistory = new ArrayList<>();
    @Deprecated(forRemoval = true)
    private List<Long> alphaSuggestionVoteHistory = new ArrayList<>();
    private List<Long> alphaSuggestionCommentHistory = new ArrayList<>();

    // Project Activity
    private long lastProjectActivity = -1L;
    private long projectVoiceJoinDate = -1L;

    // Project Suggestion Activity History
    private List<Long> projectSuggestionCreationHistory = new ArrayList<>();
    @Deprecated(forRemoval = true)
    private List<Long> projectSuggestionVoteHistory = new ArrayList<>();
    private List<Long> projectSuggestionCommentHistory = new ArrayList<>();

    private Map<String, Long> suggestionVoteHistoryMap = new HashMap<>();
    private Map<String, Long> alphaSuggestionVoteHistoryMap = new HashMap<>();
    private Map<String, Long> projectSuggestionVoteHistoryMap = new HashMap<>();

    // Channel Activity History
    private List<ChannelActivityEntry> channelActivityHistory = new ArrayList<>();
    private Map<String, Integer> channelActivity = new HashMap<>();

    // Role-Restricted Channel Activity
    private Map<String, Long> roleRestrictedChannelLastActivity = new HashMap<>();
    private Map<String, List<Long>> roleRestrictedChannelMessageHistory = new HashMap<>();
    private Map<String, List<Long>> roleRestrictedChannelCommentHistory = new HashMap<>();
    private Map<String, Map<String, Long>> roleRestrictedChannelVoteHistory = new HashMap<>();
    private Map<String, List<ChannelActivityEntry>> roleRestrictedChannelActivityHistory = new HashMap<>();

    private NominationInfo nominationInfo = new NominationInfo();

    public void addChannelHistory(GuildChannel guildChannel, long lastMessageTimestamp) {
        addChannelHistory(guildChannel, 1, lastMessageTimestamp);
    }

    public void addChannelHistory(GuildChannel guildChannel, int amount, long timestamp) {
        channelActivityHistory.stream()
            .filter(entry -> entry.getChannelId().equals(guildChannel.getId()) || entry.getLastKnownDisplayName().equalsIgnoreCase(guildChannel.getName()))
            .findFirst()
            .ifPresentOrElse(entry -> {
                entry.setMessageCount(entry.getMessageCount() + amount);
                entry.setLastMessageTimestamp(timestamp);

                String monthYear = DateFormatUtils.format(timestamp, "MM-yyyy");
                entry.getMonthlyMessageCount().merge(monthYear, amount, Integer::sum);

                if (entry.getLastKnownDisplayName() == null || !entry.getLastKnownDisplayName().equalsIgnoreCase(guildChannel.getName())) {
                    log.debug("Updating channel activity entry for channel " + entry.getLastKnownDisplayName() + " (ID: " + guildChannel.getId() + ") with new display name: " + guildChannel.getName());
                    entry.setLastKnownDisplayName(guildChannel.getName());
                }

                log.debug("Updated channel activity entry for channel " + guildChannel.getName() + " (ID: " + guildChannel.getId() + "): " + entry.getMessageCount() + " messages");
            }, () -> {
                log.debug("Adding new channel activity entry for channel " + guildChannel.getName() + " (ID: " + guildChannel.getId() + ")");
                channelActivityHistory.add(new ChannelActivityEntry(guildChannel.getId(), guildChannel.getName(), amount, timestamp, new HashMap<>(Map.of(DateFormatUtils.format(timestamp, "MM-yyyy"), amount))));
            });
    }

    /**
     * Add activity to a role-restricted channel group
     */
    public void addRoleRestrictedChannelActivity(String groupIdentifier, GuildChannel guildChannel, int amount, long timestamp) {
        roleRestrictedChannelLastActivity.put(groupIdentifier, timestamp);
        roleRestrictedChannelMessageHistory.computeIfAbsent(groupIdentifier, k -> new ArrayList<>()).add(0, timestamp);

        List<ChannelActivityEntry> groupHistory = roleRestrictedChannelActivityHistory.computeIfAbsent(groupIdentifier, k -> new ArrayList<>());
        groupHistory.stream()
            .filter(entry -> entry.getChannelId().equals(guildChannel.getId()) || entry.getLastKnownDisplayName().equalsIgnoreCase(guildChannel.getName()))
            .findFirst()
            .ifPresentOrElse(entry -> {
                entry.setMessageCount(entry.getMessageCount() + amount);
                entry.setLastMessageTimestamp(timestamp);

                String monthYear = DateFormatUtils.format(timestamp, "MM-yyyy");
                entry.getMonthlyMessageCount().merge(monthYear, amount, Integer::sum);

                if (entry.getLastKnownDisplayName() == null || !entry.getLastKnownDisplayName().equalsIgnoreCase(guildChannel.getName())) {
                    log.info("Updating role-restricted channel activity entry for group {} channel {} (ID: {}) with new display name: {}",
                        groupIdentifier, entry.getLastKnownDisplayName(), guildChannel.getId(), guildChannel.getName());
                    entry.setLastKnownDisplayName(guildChannel.getName());
                }

                log.info("Updated role-restricted channel activity entry for group {} channel {} (ID: {}): {} messages",
                    groupIdentifier, guildChannel.getName(), guildChannel.getId(), entry.getMessageCount());
            }, () -> {
                log.info("Adding new role-restricted channel activity entry for group {} channel {} (ID: {})",
                    groupIdentifier, guildChannel.getName(), guildChannel.getId());
                groupHistory.add(new ChannelActivityEntry(guildChannel.getId(), guildChannel.getName(), amount, timestamp,
                    new HashMap<>(Map.of(DateFormatUtils.format(timestamp, "MM-yyyy"), amount))));
            });
    }

    /**
     * Add comment activity to a role-restricted channel group
     */
    public void addRoleRestrictedChannelComment(String groupIdentifier, long timestamp) {
        roleRestrictedChannelCommentHistory.computeIfAbsent(groupIdentifier, k -> new ArrayList<>()).add(0, timestamp);
        roleRestrictedChannelLastActivity.put(groupIdentifier, timestamp);
    }

    /**
     * Add vote activity to a role-restricted channel group
     */
    public void addRoleRestrictedChannelVote(String groupIdentifier, String suggestionId, long timestamp) {
        roleRestrictedChannelVoteHistory.computeIfAbsent(groupIdentifier, k -> new HashMap<>()).putIfAbsent(suggestionId, timestamp);
        roleRestrictedChannelLastActivity.put(groupIdentifier, timestamp);
    }

    /**
     * Get total message count for a role-restricted channel group within specified days
     */
    public int getRoleRestrictedChannelMessageCount(String groupIdentifier, int days) {
        List<ChannelActivityEntry> entries = getRoleRestrictedChannelActivityHistory(groupIdentifier, days);
        return entries.stream().mapToInt(ChannelActivityEntry::getMessageCount).sum();
    }

    /**
     * Get total vote count for a role-restricted channel group within specified days
     */
    public int getRoleRestrictedChannelVoteCount(String groupIdentifier, int days) {
        Map<String, Long> votes = roleRestrictedChannelVoteHistory.getOrDefault(groupIdentifier, new HashMap<>());
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        return (int) votes.values().stream()
            .filter(timestamp -> timestamp >= cutoff.toEpochMilli())
            .count();
    }

    /**
     * Get total comment count for a role-restricted channel group within specified days
     */
    public int getRoleRestrictedChannelCommentCount(String groupIdentifier, int days) {
        List<Long> comments = roleRestrictedChannelCommentHistory.getOrDefault(groupIdentifier, new ArrayList<>());
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        return (int) comments.stream()
            .filter(timestamp -> timestamp >= cutoff.toEpochMilli())
            .count();
    }

    /**
     * Get channel activity history for a role-restricted channel group within specified days
     */
    public List<ChannelActivityEntry> getRoleRestrictedChannelActivityHistory(String groupIdentifier, int days) {
        List<ChannelActivityEntry> groupHistory = roleRestrictedChannelActivityHistory.getOrDefault(groupIdentifier, new ArrayList<>());
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-yyyy").withResolverStyle(ResolverStyle.SMART);
        List<ChannelActivityEntry> entries = new ArrayList<>();

        for (ChannelActivityEntry entry : groupHistory) {
            Map<String, Integer> monthlyMessageCountMap = new HashMap<>(entry.getMonthlyMessageCount());

            monthlyMessageCountMap.entrySet().removeIf(e -> {
                YearMonth date = YearMonth.parse(e.getKey(), formatter);
                return date.isBefore(YearMonth.from(startDate)) || date.isAfter(YearMonth.from(endDate));
            });

            int messageCount = monthlyMessageCountMap.values().stream().mapToInt(i -> i).sum();

            if (messageCount > 0) {
                entries.add(new ChannelActivityEntry(entry.getChannelId(), entry.getLastKnownDisplayName(),
                    messageCount, entry.getLastMessageTimestamp(), monthlyMessageCountMap));
            }
        }

        return entries;
    }

    public boolean purgeOldHistory() {
        long configuredDays = Duration.of(NerdBotApp.getBot().getConfig().getRoleConfig().getDaysRequiredForVoteHistory(), ChronoUnit.DAYS).toMillis();
        long currentTime = System.currentTimeMillis();

        return this.suggestionCreationHistory.removeIf(time -> time <= (currentTime - configuredDays)) ||
            this.suggestionVoteHistory.removeIf(time -> time <= (currentTime - configuredDays)) ||
            this.suggestionCommentHistory.removeIf(time -> time <= (currentTime - configuredDays)) ||
            this.alphaSuggestionCreationHistory.removeIf(time -> time <= (currentTime - configuredDays)) ||
            this.alphaSuggestionVoteHistory.removeIf(time -> time <= (currentTime - configuredDays)) ||
            this.alphaSuggestionCommentHistory.removeIf(time -> time <= (currentTime - configuredDays)) ||
            this.projectSuggestionCreationHistory.removeIf(time -> time <= (currentTime - configuredDays)) ||
            this.projectSuggestionVoteHistory.removeIf(time -> time <= (currentTime - configuredDays)) ||
            this.projectSuggestionCommentHistory.removeIf(time -> time <= (currentTime - configuredDays)) ||
            this.getSuggestionVoteHistoryMap().values().removeIf(time -> time <= (currentTime - configuredDays)) ||
            this.getAlphaSuggestionVoteHistoryMap().values().removeIf(time -> time <= (currentTime - configuredDays)) ||
            this.getProjectSuggestionVoteHistoryMap().values().removeIf(time -> time <= (currentTime - configuredDays)) ||
            this.roleRestrictedChannelMessageHistory.values().stream().anyMatch(messageHistory -> messageHistory.removeIf(time -> time <= (currentTime - configuredDays))) ||
            this.roleRestrictedChannelCommentHistory.values().stream().anyMatch(commentHistory -> commentHistory.removeIf(time -> time <= (currentTime - configuredDays))) ||
            this.roleRestrictedChannelVoteHistory.values().stream().anyMatch(voteHistory -> voteHistory.values().removeIf(time -> time <= (currentTime - configuredDays)));
    }

    public int getTotalMessageCount() {
        return getChannelActivityHistory().stream().mapToInt(ChannelActivityEntry::getMessageCount).sum();
    }

    public int getTotalMessageCount(int days) {
        return getChannelActivityHistory(days).stream().mapToInt(ChannelActivityEntry::getMessageCount).sum();
    }

    public List<ChannelActivityEntry> getChannelActivityHistory(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-yyyy").withResolverStyle(ResolverStyle.SMART);
        List<ChannelActivityEntry> entries = new ArrayList<>();

        for (ChannelActivityEntry entry : channelActivityHistory) {
            Map<String, Integer> monthlyMessageCountMap = entry.getMonthlyMessageCount();

            monthlyMessageCountMap.entrySet().removeIf(e -> {
                YearMonth date = YearMonth.parse(e.getKey(), formatter);
                return date.isBefore(YearMonth.from(startDate)) || date.isAfter(YearMonth.from(endDate));
            });

            int messageCount = monthlyMessageCountMap.values().stream().mapToInt(i -> i).sum();

            if (messageCount > 0) {
                entries.add(new ChannelActivityEntry(entry.getChannelId(), entry.getLastKnownDisplayName(), messageCount, entry.getLastMessageTimestamp(), monthlyMessageCountMap));
            }
        }

        return entries;
    }

    public int getTotalVotes(int days) {
        Instant instant = Instant.now().minus(Duration.ofDays(days));
        Duration duration = Duration.between(instant, Instant.now());
        int totalSuggestionVotes = (int) toTotalPeriodMap(LastActivity::getSuggestionVoteHistoryMap, duration);
        int totalAlphaSuggestionVotes = (int) toTotalPeriodMap(LastActivity::getAlphaSuggestionVoteHistoryMap, duration);
        int totalProjectSuggestionVotes = (int) toTotalPeriodMap(LastActivity::getProjectSuggestionVoteHistoryMap, duration);

        return totalSuggestionVotes + totalAlphaSuggestionVotes + totalProjectSuggestionVotes;
    }

    public int getTotalComments(int days) {
        Instant instant = Instant.now().minus(Duration.ofDays(days));
        Duration duration = Duration.between(instant, Instant.now());
        int totalSuggestionComments = toTotalPeriodNumber(LastActivity::getSuggestionCommentHistory, duration).intValue();
        int totalAlphaSuggestionComments = toTotalPeriodNumber(LastActivity::getAlphaSuggestionCommentHistory, duration).intValue();
        int totalProjectSuggestionComments = toTotalPeriodNumber(LastActivity::getProjectSuggestionCommentHistory, duration).intValue();

        return totalSuggestionComments + totalAlphaSuggestionComments + totalProjectSuggestionComments;
    }

    public String toTotalPeriodList(Function<LastActivity, List<Long>> function, Duration duration) {
        List<Long> history = function.apply(this);

        long total = history.stream()
            .filter(time -> time >= System.currentTimeMillis() - duration.toMillis())
            .count();

        return String.valueOf(total);
    }

    public long toTotalPeriodMap(Function<LastActivity, Map<String, Long>> function, Duration duration) {
        Map<String, Long> history = function.apply(this);

        return history.values().stream()
            .filter(time -> time >= System.currentTimeMillis() - duration.toMillis())
            .count();
    }

    public Number toTotalPeriodNumber(Function<LastActivity, List<Long>> function, Duration duration) {
        List<Long> history = function.apply(this);

        return history.stream()
            .filter(time -> time >= System.currentTimeMillis() - duration.toMillis())
            .count();
    }

    public String toRelativeTimestampList(Function<LastActivity, List<Long>> function) {
        List<Long> history = function.apply(this);
        long time = history.isEmpty() ? -1L : history.get(0);

        if (time <= 0) {
            return "Never";
        }

        return this.toTimestamp(__ -> time).toRelativeTimestamp();
    }

    public String toRelativeTimestampMap(Function<LastActivity, Map<String, Long>> function) {
        Map<String, Long> history = function.apply(this);
        long newestEntry = getNewestEntry(history);

        if (newestEntry <= 0) {
            return "Never";
        }

        return this.toTimestamp(__ -> newestEntry).toRelativeTimestamp();
    }

    public String toRelativeTimestamp(ToLongFunction<LastActivity> function) {
        if (function.applyAsLong(this) <= 0) {
            return "Never";
        }

        return this.toTimestamp(function).toRelativeTimestamp();
    }

    /**
     * Get relative timestamp for role-restricted channel group last activity
     */
    public String getRoleRestrictedChannelRelativeTimestamp(String groupIdentifier) {
        long lastActivity = roleRestrictedChannelLastActivity.getOrDefault(groupIdentifier, -1L);
        if (lastActivity <= 0) {
            return "Never";
        }
        return new DiscordTimestamp(lastActivity).toRelativeTimestamp();
    }

    private DiscordTimestamp toTimestamp(ToLongFunction<LastActivity> function) {
        return new DiscordTimestamp(function.applyAsLong(this));
    }

    public Long getNewestEntry(Map<String, Long> map) {
        return map.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .stream().findFirst()
            .map(Map.Entry::getValue)
            .orElse(-1L);
    }
}