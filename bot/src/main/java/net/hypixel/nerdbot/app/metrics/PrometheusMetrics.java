package net.hypixel.nerdbot.app.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.app.SkyBlockNerdsBot;

@Slf4j
public class PrometheusMetrics {

    public static final Counter EVENTS_AMOUNT = Counter.build()
        .name("events_total")
        .help("Amount of JDA events")
        .labelNames("event_name")
        .register();
    public static final Counter TOTAL_GREENLIT_MESSAGES_AMOUNT = Counter.build()
        .name("greenlit_messages")
        .help("Amount of greenlit messages")
        .register();
    public static final Counter GREENLIT_SUGGESTION_LENGTH = Counter.build()
        .name("greenlit_suggestion_length")
        .help("Length of greenlit suggestions")
        .labelNames("message_id", "message_length")
        .register();
    public static final Counter REVIEW_REQUEST_STATISTICS = Counter.build()
        .name("review_request_statistics")
        .help("Statistics of Review Requests")
        .labelNames("message_id", "user_id", "title", "state")
        .register();
    public static final Summary CURATOR_LENGTH_SECONDS = Summary.build()
        .name("curator_length_seconds")
        .help("Time taken to curate a suggestions channel in seconds")
        .labelNames("channel_name")
        .register();
    public static final Counter CURATOR_MESSAGES_AMOUNT = Counter.build()
        .name("curator_messages_total")
        .help("Amount of messages curated in a suggestions channel")
        .labelNames("channel_name")
        .register();
    public static final Gauge TOTAL_USERS_AMOUNT = Gauge.build()
        .name("guild_users_total")
        .help("Total number of users in the guild")
        .register();
    public static final Gauge TOTAL_SUGGESTIONS_AMOUNT = Gauge.build()
        .name("suggestions_total")
        .help("Total number of suggestions")
        .register();
    public static final Counter TOTAL_MESSAGES_AMOUNT = Counter.build()
        .name("guild_messages_sent_total")
        .help("Total number of messages sent in guild channels")
        .labelNames("user_name", "highest_role_name", "channel_name")
        .register();
    public static final Gauge FORUM_TAG_AMOUNT = Gauge.build()
        .name("forum_tags_total")
        .help("Total number of forum tags added")
        .labelNames("forum_tag_name")
        .register();
    public static final Counter SLASH_COMMANDS_AMOUNT = Counter.build()
        .name("slash_commands_total")
        .help("Total number of slash commands executed")
        .labelNames("user_name", "command_name")
        .register();
    public static final Counter INVITES_CREATED_AMOUNT = Counter.build()
        .name("invites_created_total")
        .help("Total number of invites created")
        .labelNames("invite_code")
        .register();
    public static final Counter INVITES_DELETED_AMOUNT = Counter.build()
        .name("invites_deleted_total")
        .help("Total number of invites deleted")
        .labelNames("invite_code")
        .register();
    public static final Gauge TOTAL_VOICE_CONNECTIONS_BY_USER = Gauge.build()
        .name("voice_connections_by_user_total")
        .help("Total number of voice connections by a user")
        .labelNames("user_name", "channel_name")
        .register();
    public static final Gauge TOTAL_VOICE_TIME_SPENT_BY_USER = Gauge.build()
        .name("voice_time_by_user_total")
        .help("Total time spent in a voice channel by a user")
        .labelNames("user_name", "channel_name")
        .register();
    public static final Counter HTTP_REQUESTS_AMOUNT = Counter.build()
        .name("http_requests_total")
        .help("Total number of HTTP requests")
        .labelNames("request_type", "url")
        .register();
    public static final Summary HTTP_REQUEST_LATENCY = Summary.build()
        .name("http_requests_latency_seconds")
        .help("Request latency in seconds")
        .labelNames("url")
        .register();

    public static final Counter TICKETS_CREATED = Counter.build()
        .name("tickets_created_total")
        .help("Total tickets created")
        .labelNames("category")
        .register();

    public static final Counter TICKETS_CLOSED = Counter.build()
        .name("tickets_closed_total")
        .help("Total tickets closed")
        .labelNames("close_type")
        .register();

    public static final Counter TICKETS_REOPENED = Counter.build()
        .name("tickets_reopened_total")
        .help("Total tickets reopened")
        .register();

    public static final Counter TICKET_MESSAGES = Counter.build()
        .name("ticket_messages_total")
        .help("Messages in tickets")
        .labelNames("author_type")
        .register();

    public static final Counter TICKET_STAFF_ACTIONS = Counter.build()
        .name("ticket_staff_actions_total")
        .help("Staff actions on tickets")
        .labelNames("action", "staff_id")
        .register();

    public static final Counter TICKET_REMINDERS_SENT = Counter.build()
        .name("ticket_reminders_sent_total")
        .help("Reminders sent for stale tickets")
        .register();

    public static final Gauge TICKETS_OPEN = Gauge.build()
        .name("tickets_open_current")
        .help("Currently open tickets")
        .labelNames("status")
        .register();

    public static final Gauge TICKETS_CLAIMED = Gauge.build()
        .name("tickets_claimed_current")
        .help("Currently claimed tickets per staff")
        .labelNames("staff_id")
        .register();

    public static final Histogram TICKET_FIRST_RESPONSE_TIME = Histogram.build()
        .name("ticket_first_response_seconds")
        .help("Time to first staff response")
        .buckets(300, 900, 1_800, 3_600, 7_200, 14_400, 28_800, 86_400)
        .register();

    public static final Histogram TICKET_RESOLUTION_TIME = Histogram.build()
        .name("ticket_resolution_seconds")
        .help("Time from creation to closure")
        .buckets(1_800, 3_600, 14_400, 86_400, 259_200, 604_800)
        .register();

    public static final Histogram TICKET_MESSAGE_COUNT = Histogram.build()
        .name("ticket_message_count")
        .help("Messages per ticket at closure")
        .buckets(1, 3, 5, 10, 20, 50, 100)
        .register();

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

                server = new HTTPServer.Builder()
                    .withPort(SkyBlockNerdsBot.config().getMetricsConfig().getPort())
                    .build();

                DefaultExports.initialize();
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