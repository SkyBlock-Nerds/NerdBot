package net.hypixel.nerdbot.app.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.HTTPServer;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;
import net.hypixel.nerdbot.marmalade.metrics.MetricsRegistry;

@Slf4j
public class PrometheusMetrics {

    public static final Counter EVENTS_AMOUNT = MetricsRegistry.counter(
        "events_total", "Amount of JDA events", "event_name");
    public static final Counter TOTAL_GREENLIT_MESSAGES_AMOUNT = MetricsRegistry.counter(
        "greenlit_messages", "Amount of greenlit messages");
    public static final Counter GREENLIT_SUGGESTION_LENGTH = MetricsRegistry.counter(
        "greenlit_suggestion_length", "Length of greenlit suggestions", "message_id", "message_length");
    public static final Counter REVIEW_REQUEST_STATISTICS = MetricsRegistry.counter(
        "review_request_statistics", "Statistics of Review Requests", "message_id", "user_id", "title", "state");
    public static final Summary CURATOR_LENGTH_SECONDS = MetricsRegistry.summary(
        "curator_length_seconds", "Time taken to curate a suggestions channel in seconds", "channel_name");
    public static final Counter CURATOR_MESSAGES_AMOUNT = MetricsRegistry.counter(
        "curator_messages_total", "Amount of messages curated in a suggestions channel", "channel_name");
    public static final Gauge TOTAL_USERS_AMOUNT = MetricsRegistry.gauge(
        "guild_users_total", "Total number of users in the guild");
    public static final Gauge TOTAL_SUGGESTIONS_AMOUNT = MetricsRegistry.gauge(
        "suggestions_total", "Total number of suggestions");
    public static final Counter TOTAL_MESSAGES_AMOUNT = MetricsRegistry.counter(
        "guild_messages_sent_total", "Total number of messages sent in guild channels", "user_name", "highest_role_name", "channel_name");
    public static final Gauge FORUM_TAG_AMOUNT = MetricsRegistry.gauge(
        "forum_tags_total", "Total number of forum tags added", "forum_tag_name");
    public static final Counter SLASH_COMMANDS_AMOUNT = MetricsRegistry.counter(
        "slash_commands_total", "Total number of slash commands executed", "user_name", "command_name");
    public static final Counter INVITES_CREATED_AMOUNT = MetricsRegistry.counter(
        "invites_created_total", "Total number of invites created", "invite_code");
    public static final Counter INVITES_DELETED_AMOUNT = MetricsRegistry.counter(
        "invites_deleted_total", "Total number of invites deleted", "invite_code");
    public static final Gauge TOTAL_VOICE_CONNECTIONS_BY_USER = MetricsRegistry.gauge(
        "voice_connections_by_user_total", "Total number of voice connections by a user", "user_name", "channel_name");
    public static final Gauge TOTAL_VOICE_TIME_SPENT_BY_USER = MetricsRegistry.gauge(
        "voice_time_by_user_total", "Total time spent in a voice channel by a user", "user_name", "channel_name");
    public static final Counter HTTP_REQUESTS_AMOUNT = MetricsRegistry.counter(
        "http_requests_total", "Total number of HTTP requests", "request_type", "url");
    public static final Summary HTTP_REQUEST_LATENCY = MetricsRegistry.summary(
        "http_requests_latency_seconds", "Request latency in seconds", "url");

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
