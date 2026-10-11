package net.hypixel.nerdbot.discord;

import net.hypixel.nerdbot.discord.api.feature.BotFeature;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;
import net.hypixel.nerdbot.marmalade.storage.database.Database;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFileTest {

    @TempDir
    Path folder;

    private Path file;
    private String previousProperty;
    private final TestBot bot = new TestBot();

    @BeforeEach
    void pointAtTempFile() {
        file = folder.resolve("bot.config.json");
        previousProperty = System.getProperty("bot.config");
        System.setProperty("bot.config", file.toString());
    }

    @AfterEach
    void restoreProperty() {
        if (previousProperty == null) {
            System.clearProperty("bot.config");
        } else {
            System.setProperty("bot.config", previousProperty);
        }
    }

    @Test
    void theFileComesFromTheProperty() {
        assertEquals(file, bot.configFile());
    }

    @Test
    void savingWritesTheFileAndMakesTheConfigLive() throws IOException {
        DiscordBotConfig config = new DiscordBotConfig();
        config.setGuildId("123");

        bot.saveConfig(config);

        assertSame(config, bot.getConfig());
        assertTrue(Files.readString(file).contains("\"guildId\": \"123\""));
        assertFalse(Files.exists(folder.resolve("bot.config.json.tmp")), "the temporary file is moved into place");
    }

    @Test
    void savingReplacesAnExistingFile() throws IOException {
        Files.writeString(file, "{\"guildId\": \"old\"}");
        DiscordBotConfig config = new DiscordBotConfig();
        config.setGuildId("new");

        bot.saveConfig(config);
        bot.loadConfig();

        assertEquals("new", bot.getConfig().getGuildId());
    }

    @Test
    void aFileThatWontParseLeavesTheLiveConfigAlone() throws IOException {
        DiscordBotConfig live = new DiscordBotConfig();
        bot.saveConfig(live);
        Files.writeString(file, "{ not json");

        assertThrows(IllegalStateException.class, bot::loadConfig);
        assertSame(live, bot.getConfig());
    }

    @Test
    void writeConfigReportsFailureInsteadOfThrowing() {
        System.setProperty("bot.config", folder.resolve("missing").resolve("bot.config.json").toString());

        assertFalse(bot.writeConfig(new DiscordBotConfig()));
    }

    private static final class TestBot extends AbstractDiscordBot {

        @Override
        protected @NotNull Database createDatabase() {
            return new Database(null, null);
        }

        @Override
        protected @NotNull Collection<Object> getEventListeners() {
            return List.of();
        }

        @Override
        protected @NotNull Collection<? extends BotFeature> createFeatures() {
            return List.of();
        }

        @Override
        protected void onShutdown() {
        }
    }
}
