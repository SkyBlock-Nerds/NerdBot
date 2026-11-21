package net.hypixel.nerdbot.discord.util;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecretsTest {

    @Test
    void theEnvironmentVariableIsTheNameInUpperCaseWithUnderscores() {
        assertEquals("BOT_TOKEN", Secrets.environmentName("bot.token"));
        assertEquals("DB_MONGODB_URI", Secrets.environmentName("db.mongodb.uri"));
    }

    @Test
    void aSecretIsReadFromTheEnvironment() {
        assertEquals(Optional.of("from-env"), find(Map.of("BOT_TOKEN", "from-env"), Map.of()));
    }

    @Test
    void aSecretFallsBackToTheSystemProperty() {
        assertEquals(Optional.of("from-property"), find(Map.of(), Map.of("bot.token", "from-property")));
    }

    @Test
    void theEnvironmentWinsOverTheSystemProperty() {
        assertEquals(Optional.of("from-env"), find(Map.of("BOT_TOKEN", "from-env"), Map.of("bot.token", "from-property")));
    }

    @Test
    void aBlankValueCountsAsUnset() {
        assertEquals(Optional.of("from-property"), find(Map.of("BOT_TOKEN", " "), Map.of("bot.token", "from-property")));
        assertEquals(Optional.empty(), find(Map.of("BOT_TOKEN", ""), Map.of("bot.token", "")));
        assertEquals(Optional.empty(), find(Map.of(), Map.of()));
    }

    @Test
    void aSecretIsReadFromTheRunningJvm() {
        String name = "nerdbot.test.secret.that.is.not.in.the.environment";
        System.setProperty(name, "from-property");
        try {
            assertEquals(Optional.of("from-property"), Secrets.find(name));
        } finally {
            System.clearProperty(name);
        }
        assertEquals(Optional.empty(), Secrets.find(name));
    }

    private static Optional<String> find(Map<String, String> environment, Map<String, String> properties) {
        return Secrets.find("bot.token", environment::get, properties::get);
    }
}
