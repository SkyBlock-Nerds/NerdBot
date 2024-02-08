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

    public static String toShortTime(long timestamp) {
        return new DiscordTimestamp(timestamp / 1_000).toShortTime();
    }

    public String toLongTime() {
        return TIMESTAMP_FORMAT.formatted("t", timestamp, "T");
    }

    public static String toLongTime(long timestamp) {
        return new DiscordTimestamp(timestamp / 1_000).toLongTime();
    }

    public String toShortDate() {
        return TIMESTAMP_FORMAT.formatted("t", timestamp, "d");
    }

    public static String toShortDate(long timestamp) {
        return new DiscordTimestamp(timestamp / 1_000).toShortDate();
    }

    public String toLongDate() {
        return TIMESTAMP_FORMAT.formatted("t", timestamp, "D");
    }

    public static String toLongDate(long timestamp) {
        return new DiscordTimestamp(timestamp / 1_000).toLongDate();
    }

    public String toShortDateTime() {
        return TIMESTAMP_FORMAT.formatted("t", timestamp, "f");
    }

    public static String toShortDateTime(long timestamp) {
        return new DiscordTimestamp(timestamp / 1_000).toShortDateTime();
    }

    public String toLongDateTime() {
        return TIMESTAMP_FORMAT.formatted("t", timestamp, "F");
    }

    public static String toLongDateTime(long timestamp) {
        return new DiscordTimestamp(timestamp / 1_000).toLongDateTime();
    }

    public String toRelativeTimestamp() {
        return TIMESTAMP_FORMAT.formatted("t", timestamp, "R");
    }

    public static String toRelativeTimestamp(long timestamp) {
        return new DiscordTimestamp(timestamp / 1_000).toRelativeTimestamp();
    }
}
