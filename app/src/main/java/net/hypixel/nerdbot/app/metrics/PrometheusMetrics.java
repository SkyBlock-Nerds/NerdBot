package net.hypixel.nerdbot.app.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.HTTPServer;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.marmalade.metrics.MetricsRegistry;
import net.hypixel.nerdbot.marmalade.metrics.NamespacedMetrics;

@Slf4j
public class PrometheusMetrics {

    private static final NamespacedMetrics METRICS = MetricsRegistry.namespace("nerdbot");

    public static final Counter EVENTS_AMOUNT = METRICS.counter(
        "events_total", "Amount of JDA events", "event_name");
    public static final Counter TOTAL_GREENLIT_MESSAGES_AMOUNT = METRICS.counter(
        "greenlit_messages", "Amount of greenlit messages");
    public static final Summary GREENLIT_SUGGESTION_LENGTH = METRICS.summary(
        "greenlit_suggestion_length_chars", "Length of greenlit suggestions in characters");
    public static final Counter REVIEW_REQUESTS = METRICS.counter(
        "review_requests_total", "Review requests handled, by outcome", "state");
    public static final Summary CURATOR_LENGTH_SECONDS = METRICS.summary(
        "curator_length_seconds", "Time taken to curate a suggestions channel in seconds", "channel_name");
    public static final Counter CURATOR_MESSAGES_AMOUNT = METRICS.counter(
        "curator_messages_total", "Amount of messages curated in a suggestions channel", "channel_name");
    public static final Gauge TOTAL_USERS_AMOUNT = METRICS.gauge(
        "guild_users_total", "Total number of users in the guild");
    public static final Gauge TOTAL_SUGGESTIONS_AMOUNT = METRICS.gauge(
        "suggestions_total", "Number of suggestions currently in the suggestion cache");
    public static final Counter TOTAL_MESSAGES_AMOUNT = METRICS.counter(
        "guild_messages_sent_total", "Total number of messages sent in guild channels", "highest_role_name", "channel_name");
    public static final Gauge FORUM_TAG_AMOUNT = METRICS.gauge(
        "forum_tags_total", "Total number of forum tags added", "forum_tag_name");
    public static final Counter SLASH_COMMANDS_AMOUNT = METRICS.counter(
        "slash_commands_total", "Total number of slash commands executed", "command_name");
    public static final Counter INVITES_CREATED_AMOUNT = METRICS.counter(
        "invites_created_total", "Total number of invites created");
    public static final Counter INVITES_DELETED_AMOUNT = METRICS.counter(
        "invites_deleted_total", "Total number of invites deleted");
    public static final Counter VOICE_CONNECTIONS = METRICS.counter(
        "voice_connections_total", "Number of times a member joined a voice channel", "channel_name");
    public static final Counter VOICE_TIME_SECONDS = METRICS.counter(
        "voice_time_seconds_total", "Seconds members spent in a voice channel", "channel_name");
    public static final Counter HTTP_REQUESTS_AMOUNT = METRICS.counter(
        "http_requests_total", "Total number of HTTP requests", "request_type", "endpoint");
    public static final Summary HTTP_REQUEST_LATENCY = METRICS.summary(
        "http_requests_latency_seconds", "Request latency in seconds", "endpoint");

    private static HTTPServer server;

    private PrometheusMetrics() {
    }

    public static void setMetricsEnabled(boolean enableMetrics) {
        SkyBlockNerdsBot.config().getMetricsConfig().setEnabled(enableMetrics);

        if (enableMetrics) {
            log.info("Starting Prometheus metrics server...");

            try {
                if (server != null) {
                    server.close();
                }

                server = MetricsRegistry.startServer(SkyBlockNerdsBot.config().getMetricsConfig().getPort());
                log.info("Enabled Prometheus metrics on port {}",
                    SkyBlockNerdsBot.config().getMetricsConfig().getPort());
            } catch (Exception exception) {
                log.error("Failed to start Prometheus metrics server!", exception);
            }
        } else {
            log.info("Disabling Prometheus metrics...");

            if (server != null) {
                try {
                    server.close();
                    server = null;
                    log.info("Prometheus metrics server stopped");
                } catch (Exception exception) {
                    log.error("Failed to stop Prometheus metrics server!", exception);
                }
            }
        }
    }
}
