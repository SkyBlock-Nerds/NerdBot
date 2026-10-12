package net.hypixel.nerdbot.app.listener;

import net.hypixel.nerdbot.app.config.NerdBotConfig;
import org.junit.jupiter.api.Test;

import static net.hypixel.nerdbot.app.testsupport.MemberFakes.MAIN_GUILD_ID;
import static net.hypixel.nerdbot.app.testsupport.MemberFakes.guild;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildScopeTest {

    private final NerdBotConfig config = new NerdBotConfig();
    private final GuildScope scope = new GuildScope(() -> config);

    @Test
    void onlyTheConfiguredGuildIsInScope() {
        config.setGuildId(MAIN_GUILD_ID);

        assertTrue(scope.isMainGuild(guild(MAIN_GUILD_ID)));
        assertFalse(scope.isMainGuild(guild("200")));
    }

    @Test
    void noGuildIsOutOfScope() {
        config.setGuildId(MAIN_GUILD_ID);

        assertFalse(scope.isMainGuild(null));
    }

    @Test
    void theConfigIsReadEachTime() {
        config.setGuildId("200");
        assertFalse(scope.isMainGuild(guild(MAIN_GUILD_ID)));

        config.setGuildId(MAIN_GUILD_ID);
        assertTrue(scope.isMainGuild(guild(MAIN_GUILD_ID)));
    }
}
