package net.hypixel.nerdbot.discord.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
@ToString
public class WatcherConfig {

    /**
     * Whether this watcher should be enabled at startup.
     * Defaults to {@code true}
     */
    private boolean enabled = true;

    /**
     * Fully qualified class name of the watcher implementation.
     * Must extend {@code net.hypixel.nerdbot.app.urlwatcher.URLWatcher} and expose a public
     * constructor of either (String url, Map<String,String> headers) or (String url)
     */
    private String className;

    /**
     * Endpoint URL to poll.
     */
    private String url;

    /**
     * Optional HTTP headers to send with the request (e.g., User-Agent, Accept).
     * Default: no additional headers
     */
    private Map<String, String> headers;

    /**
     * Polling interval value to use with {@link #timeUnit}.
     * Default: 1
     */
    private long interval = 1;

    /**
     * Time unit for {@link #interval}.
     * Default: MINUTES
     */
    private TimeUnit timeUnit = TimeUnit.MINUTES;

    /**
     * Optional fully qualified class name of a handler implementing {@code net.hypixel.nerdbot.app.urlwatcher.URLWatcher.DataHandler}.
     * Required for watchers that emit JSON diffs (e.g., JsonURLWatcher). Not required for
     * watchers that do their own downstream processing (e.g., HypixelThreadURLWatcher).
     */
    private String handlerClass;
}
