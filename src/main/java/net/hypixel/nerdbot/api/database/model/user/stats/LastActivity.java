package net.hypixel.nerdbot.api.database.model.user.stats;

import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToLongFunction;

@Getter
@Setter
public class LastActivity {

    // Global Activity
    private long lastGlobalActivity = -1L;
    private long lastVoiceChannelJoinDate = -1L;
    private long lastItemGenUsage = -1L;
    private long lastModMailUsage = -1L;

    // Suggestion Activity
    private long lastSuggestionDate = -1L;
    private long suggestionVoteDate = -1L;
    private long suggestionCommentDate = -1L;

    // Alpha Activity
    private long lastAlphaActivity = -1L;
    private long alphaVoiceJoinDate = -1L;

    // Alpha Suggestion Activity
    private long lastAlphaSuggestionDate = -1L;
    private long alphaSuggestionVoteDate = -1L;
    private long alphaSuggestionCommentDate = -1L;

    private Map<String, Integer> channelActivity = new HashMap<>();

    public LastActivity() {
    }

    public DiscordTimestamp toTimestamp(ToLongFunction<LastActivity> function) {
        return new DiscordTimestamp(function.applyAsLong(this));
    }

    public String toRelativeTimestamp(ToLongFunction<LastActivity> function) {
        if (function.applyAsLong(this) <= 0) {
            return "Never";
        }

        return this.toTimestamp(function).toRelativeTimestamp();
    }
}
