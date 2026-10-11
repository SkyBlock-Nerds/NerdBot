package net.hypixel.nerdbot.app;

import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class LoggingBackendTest {

    // A transitive slf4j-api 1.7 winning dependency mediation can't find logback 1.6, so SLF4J silently falls back
    // to its no-op logger and the bot runs with no logs at all
    @Test
    void slf4jRoutesToLogback() {
        assertInstanceOf(LoggerContext.class, LoggerFactory.getILoggerFactory());
    }
}
