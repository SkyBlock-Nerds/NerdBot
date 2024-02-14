package net.hypixel.nerdbot.api.database.model.user.stats;

import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // TODO: Remove Old Suggestion Activity
    private long lastSuggestionDate = -1L;
    private long suggestionVoteDate = -1L;
    private long suggestionCommentDate = -1L;

    // Suggestion Activity History
    private List<Long> suggestionCreationHistory = new ArrayList<>();
    private List<Long> suggestionVoteHistory = new ArrayList<>();
    private List<Long> suggestionCommentHistory = new ArrayList<>();

    // Alpha Activity
    private long lastAlphaActivity = -1L;
    private long alphaVoiceJoinDate = -1L;

    // TODO: Remove Old Alpha Suggestion Activity
    private long lastAlphaSuggestionDate = -1L;
    private long alphaSuggestionVoteDate = -1L;
    private long alphaSuggestionCommentDate = -1L;

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

    private Map<String, Integer> channelActivity = new HashMap<>();

    public void migrateToHistory() {
        if (this.lastSuggestionDate != -1)
            this.suggestionCreationHistory.add(this.lastSuggestionDate);
        if (this.suggestionVoteDate != -1)
            this.suggestionVoteHistory.add(this.suggestionVoteDate);
        if (this.suggestionCommentDate != -1)
            this.suggestionCommentHistory.add(this.suggestionCommentDate);

        if (this.lastAlphaSuggestionDate != -1)
            this.alphaSuggestionCreationHistory.add(this.lastAlphaSuggestionDate);
        if (this.alphaSuggestionVoteDate != -1)
            this.alphaSuggestionVoteHistory.add(this.alphaSuggestionVoteDate);
        if (this.alphaSuggestionCommentDate != -1)
            this.alphaSuggestionCommentHistory.add(this.alphaSuggestionCommentDate);

        this.lastSuggestionDate = -1;
        this.suggestionVoteDate = -1;
        this.suggestionCommentDate = -1;
        this.lastAlphaSuggestionDate = -1;
        this.alphaSuggestionVoteDate = -1;
        this.alphaSuggestionCommentDate = -1;
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
