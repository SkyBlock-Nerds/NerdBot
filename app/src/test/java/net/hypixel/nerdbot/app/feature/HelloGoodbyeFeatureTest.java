package net.hypixel.nerdbot.app.feature;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.hypixel.nerdbot.app.testsupport.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HelloGoodbyeFeatureTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-12T12:00:00Z"));
    private final List<MessageEmbed> logged = new ArrayList<>();
    private final HelloGoodbyeFeature feature = new HelloGoodbyeFeature(logged::add, clock);

    @Test
    void eachMessageIsStampedWhenItsSent() {
        feature.onFeatureStart();
        clock.advance(Duration.ofDays(3));
        feature.onFeatureEnd();

        assertEquals(List.of("Hello there!", "Goodbye cruel world!"), logged.stream().map(MessageEmbed::getTitle).toList());
        assertEquals(Instant.parse("2026-10-12T12:00:00Z"), logged.get(0).getTimestamp().toInstant());
        assertEquals(Instant.parse("2026-10-15T12:00:00Z"), logged.get(1).getTimestamp().toInstant());
    }
}
