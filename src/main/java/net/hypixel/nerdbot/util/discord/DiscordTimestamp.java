package net.hypixel.nerdbot.util.discord;

public class DiscordTimestamp {

    public static final String TIMESTAMP_FORMAT = "<%s:%d:%s>";

    private final long timestamp;

    public DiscordTimestamp(long timestamp) {
        this.timestamp = timestamp / 1_000;
    }

    public String toShortTime() {
        return TIMESTAMP_FORMAT.formatted("t", timestamp, "t");
    }

    public String toLongTime() {
        return TIMESTAMP_FORMAT.formatted("t", timestamp, "T");
    }

    public String toShortDate() {
        return TIMESTAMP_FORMAT.formatted("t", timestamp, "d");
    }

    public String toLongDate() {
        return TIMESTAMP_FORMAT.formatted("t", timestamp, "D");
    }

    public String toShortDateTime() {
        return TIMESTAMP_FORMAT.formatted("t", timestamp, "f");
    }

    public String toLongDateTime() {
        return TIMESTAMP_FORMAT.formatted("t", timestamp, "F");
    }

    public String toRelativeTimestamp() {
        return TIMESTAMP_FORMAT.formatted("t", timestamp, "R");
    }
}
