package net.hypixel.nerdbot.app.metrics;

import io.prometheus.client.SimpleCollector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every label value becomes its own time series that lives until restart, so labels must come from
 * small fixed sets. Anything per user, message, invite or URL is off limits. Adding a metric means
 * adding it here, which forces a look at its labels.
 */
class MetricLabelsTest {

    private static final Map<String, List<String>> EXPECTED_LABELS = Map.ofEntries(
        entry("EVENTS_AMOUNT", List.of("event_name")),
        entry("TOTAL_GREENLIT_MESSAGES_AMOUNT", List.of()),
        entry("GREENLIT_SUGGESTION_LENGTH", List.of()),
        entry("REVIEW_REQUESTS", List.of("state")),
        entry("CURATOR_LENGTH_SECONDS", List.of("channel_name")),
        entry("CURATOR_MESSAGES_AMOUNT", List.of("channel_name")),
        entry("TOTAL_USERS_AMOUNT", List.of()),
        entry("TOTAL_SUGGESTIONS_AMOUNT", List.of()),
        entry("TOTAL_MESSAGES_AMOUNT", List.of("highest_role_name", "channel_name")),
        entry("FORUM_TAG_AMOUNT", List.of("forum_tag_name")),
        entry("SLASH_COMMANDS_AMOUNT", List.of("command_name")),
        entry("INVITES_CREATED_AMOUNT", List.of()),
        entry("INVITES_DELETED_AMOUNT", List.of()),
        entry("VOICE_CONNECTIONS", List.of("channel_name")),
        entry("VOICE_TIME_SECONDS", List.of("channel_name")),
        entry("HTTP_REQUESTS_AMOUNT", List.of("request_type", "endpoint")),
        entry("HTTP_REQUEST_LATENCY", List.of("endpoint"))
    );

    @Test
    void everyMetricHasExactlyTheReviewedLabels() throws ReflectiveOperationException {
        assertEquals(new TreeMap<>(EXPECTED_LABELS), new TreeMap<>(actualLabels()));
    }

    @Test
    void everyMetricIsNamespacedForTheSharedGrafana() throws ReflectiveOperationException {
        Field fullname = SimpleCollector.class.getDeclaredField("fullname");
        fullname.setAccessible(true);

        for (Field field : metricFields()) {
            String name = (String) fullname.get(field.get(null));
            assertTrue(name.startsWith("nerdbot_"), field.getName() + " is named " + name);
        }
    }

    private static Map<String, List<String>> actualLabels() throws ReflectiveOperationException {
        Field labelNames = SimpleCollector.class.getDeclaredField("labelNames");
        labelNames.setAccessible(true);

        Map<String, List<String>> labels = new TreeMap<>();
        for (Field field : metricFields()) {
            @SuppressWarnings("unchecked")
            List<String> names = (List<String>) labelNames.get(field.get(null));
            labels.put(field.getName(), List.copyOf(names));
        }
        return labels;
    }

    private static List<Field> metricFields() {
        return Arrays.stream(PrometheusMetrics.class.getDeclaredFields())
            .filter(field -> Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers()))
            .filter(field -> SimpleCollector.class.isAssignableFrom(field.getType()))
            .toList();
    }
}
