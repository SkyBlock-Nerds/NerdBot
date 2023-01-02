package net.hypixel.nerdbot.api.database.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LastActivity {

    private long lastGlobalActivity = -1L;
    private long lastVoiceChannelJoinDate = -1L;
    private long lastSuggestionDate = -1L;
    private long lastAlphaSuggestionDate = -1L;
    private long lastAlphaActivity = -1L;
    private long lastItemGenUsage = -1L;

    public LastActivity() {
    }
}
