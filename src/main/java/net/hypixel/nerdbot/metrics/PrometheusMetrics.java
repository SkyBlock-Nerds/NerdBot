package net.hypixel.nerdbot.metrics;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Summary;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.NerdBotApp;

@Log4j2
public class PrometheusMetrics {

    private static final CollectorRegistry collectorRegistry = new CollectorRegistry();

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

    private static HTTPServer server;

    public static void setMetricsEnabled(boolean enableMetrics) {
        NerdBotApp.getBot().getConfig().getMetricsConfig().setEnabled(enableMetrics);

        collectorRegistry.clear();

        if (NerdBotApp.getBot().getConfig().getMetricsConfig().isEnabled()) {
            log.info("Starting Prometheus metrics server...");

            try {
                if (server != null) {
                    server.close();
                }

                server = new HTTPServer.Builder()
                    .withPort(NerdBotApp.getBot().getConfig().getMetricsConfig().getPort())
                    .build();

                DefaultExports.initialize();
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            collectorRegistry.register(TOTAL_GREENLIT_MESSAGES_AMOUNT);
            collectorRegistry.register(GREENLIT_SUGGESTION_LENGTH);
            collectorRegistry.register(TOTAL_USERS_AMOUNT);
            collectorRegistry.register(TOTAL_SUGGESTIONS_AMOUNT);
            collectorRegistry.register(TOTAL_MESSAGES_AMOUNT);
            collectorRegistry.register(FORUM_TAG_AMOUNT);
            collectorRegistry.register(SLASH_COMMANDS_AMOUNT);
            collectorRegistry.register(INVITES_CREATED_AMOUNT);
            collectorRegistry.register(INVITES_DELETED_AMOUNT);
            collectorRegistry.register(TOTAL_VOICE_CONNECTIONS_BY_USER);
            collectorRegistry.register(TOTAL_VOICE_TIME_SPENT_BY_USER);
            collectorRegistry.register(HTTP_REQUESTS_AMOUNT);
            collectorRegistry.register(HTTP_REQUEST_LATENCY);

            log.info("Enabled Prometheus metrics!");
        } else {
            collectorRegistry.unregister(TOTAL_GREENLIT_MESSAGES_AMOUNT);
            collectorRegistry.unregister(GREENLIT_SUGGESTION_LENGTH);
            collectorRegistry.unregister(TOTAL_USERS_AMOUNT);
            collectorRegistry.unregister(TOTAL_SUGGESTIONS_AMOUNT);
            collectorRegistry.unregister(TOTAL_MESSAGES_AMOUNT);
            collectorRegistry.unregister(FORUM_TAG_AMOUNT);
            collectorRegistry.unregister(SLASH_COMMANDS_AMOUNT);
            collectorRegistry.unregister(INVITES_CREATED_AMOUNT);
            collectorRegistry.unregister(INVITES_DELETED_AMOUNT);
            collectorRegistry.unregister(TOTAL_VOICE_CONNECTIONS_BY_USER);
            collectorRegistry.unregister(TOTAL_VOICE_TIME_SPENT_BY_USER);
            collectorRegistry.unregister(HTTP_REQUESTS_AMOUNT);
            collectorRegistry.unregister(HTTP_REQUEST_LATENCY);

            log.info("Disabled Prometheus metrics!");
        }
    }


    private PrometheusMetrics() {
    }
}
