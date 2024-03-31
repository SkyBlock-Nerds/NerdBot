package net.hypixel.nerdbot.api.database.model.user.stats;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;
import org.apache.commons.lang.time.DateFormatUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    private List<Long> suggestionVoteHistory = new ArrayList<>();
    private List<Long> suggestionCommentHistory = new ArrayList<>();

    // Alpha Activity
    private long lastAlphaActivity = -1L;
    private long alphaVoiceJoinDate = -1L;

    // Alpha Suggestion Activity History
    private List<Long> alphaSuggestionCreationHistory = new ArrayList<>();
    private List<Long> alphaSuggestionVoteHistory = new ArrayList<>();
    private List<Long> alphaSuggestionCommentHistory = new ArrayList<>();

    // Project Activity
    private long lastProjectActivity = -1L;
    private long projectVoiceJoinDate = -1L;

    // Project Suggestion Activity History
    private List<Long> projectSuggestionCreationHistory = new ArrayList<>();
    private List<Long> projectSuggestionVoteHistory = new ArrayList<>();
    private List<Long> projectSuggestionCommentHistory = new ArrayList<>();

    private List<ChannelActivityEntry> channelActivityHistory = new ArrayList<>();
    private Map<String, Integer> channelActivity = new HashMap<>();

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

    public boolean purgeOldHistory() {
        long thirtyDays = Duration.of(30, ChronoUnit.DAYS).toMillis();
        long currentTime = System.currentTimeMillis();

        return this.suggestionCreationHistory.removeIf(time -> time <= (currentTime - thirtyDays)) ||
            this.suggestionVoteHistory.removeIf(time -> time <= (currentTime - thirtyDays)) ||
            this.suggestionCommentHistory.removeIf(time -> time <= (currentTime - thirtyDays)) ||
            this.alphaSuggestionCreationHistory.removeIf(time -> time <= (currentTime - thirtyDays)) ||
            this.alphaSuggestionVoteHistory.removeIf(time -> time <= (currentTime - thirtyDays)) ||
            this.alphaSuggestionCommentHistory.removeIf(time -> time <= (currentTime - thirtyDays)) ||
            this.projectSuggestionCreationHistory.removeIf(time -> time <= (currentTime - thirtyDays)) ||
            this.projectSuggestionVoteHistory.removeIf(time -> time <= (currentTime - thirtyDays)) ||
            this.projectSuggestionCommentHistory.removeIf(time -> time <= (currentTime - thirtyDays));
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
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-yyyy");
        List<ChannelActivityEntry> entries = new ArrayList<>();

        for (ChannelActivityEntry entry : channelActivityHistory) {
            log.info("Entry: " + entry.getLastKnownDisplayName() + " " + entry.getLastMessageTimestamp() + " " + entry.getMonthlyMessageCount());

            Map<String, Integer> monthlyMessageCountMap = entry.getMonthlyMessageCount();

            monthlyMessageCountMap.entrySet().removeIf(e -> {
                LocalDate date = formatter.parse(e.getKey(), LocalDate::from);
                return date.isBefore(startDate) || date.isAfter(endDate);
            });

            int messageCount = monthlyMessageCountMap.values().stream().mapToInt(i -> i).sum();

            if (messageCount > 0) {
                entries.add(new ChannelActivityEntry(entry.getChannelId(), entry.getLastKnownDisplayName(), messageCount, entry.getLastMessageTimestamp(), monthlyMessageCountMap));
                log.info("Added entry for channel " + entry.getLastKnownDisplayName() + " with " + messageCount + " messages, from " + startDate.format(formatter) + " to " + endDate.format(formatter));
                log.info("Monthly message count map: " + monthlyMessageCountMap);
            } else {
                log.info("Skipping entry for channel " + entry.getLastKnownDisplayName() + " with 0 messages, from " + startDate.format(formatter) + " to " + endDate.format(formatter));
            }
        }

        return entries;
    }

    public String toTotalPeriod(Function<LastActivity, List<Long>> function, Duration duration) {
        List<Long> history = function.apply(this);

        long total = history.stream()
            .filter(time -> time >= System.currentTimeMillis() - duration.toMillis())
            .count();

        return String.valueOf(total);
    }

    public String toRelativeTimestamp(Function<LastActivity, List<Long>> function) {
        List<Long> history = function.apply(this);
        long time = history.isEmpty() ? -1L : history.get(0);

        if (time <= 0) {
            return "Never";
        }

        return this.toTimestamp(__ -> time).toRelativeTimestamp();
    }

    public String toRelativeTimestamp(ToLongFunction<LastActivity> function) {
        if (function.applyAsLong(this) <= 0) {
            return "Never";
        }

        return this.toTimestamp(function).toRelativeTimestamp();
    }

    private DiscordTimestamp toTimestamp(ToLongFunction<LastActivity> function) {
        return new DiscordTimestamp(function.applyAsLong(this));
    }
}
