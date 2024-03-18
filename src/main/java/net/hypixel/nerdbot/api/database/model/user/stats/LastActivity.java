package net.hypixel.nerdbot.api.database.model.user.stats;

import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.ToLongFunction;

@Getter
@Setter
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
            .filter(entry -> entry.getChannelId().equals(guildChannel.getId()))
            .findFirst()
            .ifPresentOrElse(entry -> {
                entry.setMessageCount(entry.getMessageCount() + amount);
                entry.setLastMessageTimestamp(timestamp);

                if (entry.getLastKnownDisplayName() == null || !entry.getLastKnownDisplayName().equalsIgnoreCase(guildChannel.getName())) {
                    entry.setLastKnownDisplayName(guildChannel.getName());
                }
            }, () -> channelActivityHistory.add(new ChannelActivityEntry(guildChannel.getId(), guildChannel.getName(), amount, timestamp)));
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
        return getChannelActivityHistory().stream()
            .filter(entry -> entry.getLastMessageTimestamp() > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days))
            .mapToInt(ChannelActivityEntry::getMessageCount)
            .sum();
    }

    public List<ChannelActivityEntry> getChannelActivityHistory(int days) {
        return getChannelActivityHistory().stream()
            .filter(entry -> entry.getLastMessageTimestamp() > System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days))
            .toList();
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
