package net.hypixel.nerdbot.api.database.user;

import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.util.discord.DiscordTimestamp;

import java.util.function.Function;

@Getter
@Setter
public class LastActivity {

    // Global Activity
    private long lastGlobalActivity = -1L;
    private long lastVoiceChannelJoinDate = -1L;
    private long lastItemGenUsage = -1L;

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

    public LastActivity() {
    }

    public DiscordTimestamp toTimestamp(Function<LastActivity, Long> function) {
        return new DiscordTimestamp(function.apply(this));
    }

    public String toRelativeTimestamp(Function<LastActivity, Long> function) {
        return this.toTimestamp(function).toRelativeTimestamp();
    }
}
