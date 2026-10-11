package net.hypixel.nerdbot.app;

import net.dv8tion.jda.api.entities.Activity;
import net.hypixel.nerdbot.discord.config.DiscordBotConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ActivityTypeMappingTest {

    @Test
    void customMapsToJdasCustomStatus() {
        assertEquals(Activity.ActivityType.CUSTOM_STATUS, DiscordBotConfig.ActivityType.CUSTOM.toJda());
    }

    @ParameterizedTest
    @EnumSource(DiscordBotConfig.ActivityType.class)
    void everyConfigurableTypeBuildsAnActivity(DiscordBotConfig.ActivityType type) {
        Activity activity = assertDoesNotThrow(() -> Activity.of(type.toJda(), "with nerds"));

        assertEquals("with nerds", activity.getName());
    }
}
