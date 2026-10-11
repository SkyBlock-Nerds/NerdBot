package net.hypixel.nerdbot.app.channel;

import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.app.testsupport.ChannelFakes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChannelAccessTest {

    private final ChannelFakes discord = new ChannelFakes();
    private final Role staff = discord.addRole("10", "Staff");
    private final Role alpha = discord.addRole("11", "Alpha");

    @Test
    void aChannelWithoutAnEveryoneDenyIsPublic() {
        assertEquals("Public", ChannelAccess.describe(discord.publicChannel("1")));
        assertEquals("🌐 Publicly accessible", ChannelAccess.summary(discord.publicChannel("2")));
    }

    @Test
    void aHiddenChannelNobodyIsAllowedIntoIsPrivate() {
        assertEquals("Private - No roles allowed", ChannelAccess.describe(discord.hiddenChannel("1")));
        assertEquals("🔒 @everyone denied: `VIEW_CHANNEL`\n❌ No roles explicitly allowed", ChannelAccess.summary(discord.hiddenChannel("2")));
    }

    @Test
    void oneAllowedRoleIsNamed() {
        assertEquals("Role-restricted: Staff", ChannelAccess.describe(discord.hiddenChannel("1", staff)));
    }

    @Test
    void severalAllowedRolesAreCounted() {
        assertEquals("Role-restricted: 2 roles", ChannelAccess.describe(discord.hiddenChannel("1", staff, alpha)));
        assertEquals("🔒 @everyone denied: `VIEW_CHANNEL`\n✅ Allowed roles: Staff, Alpha", ChannelAccess.summary(discord.hiddenChannel("2", staff, alpha)));
    }
}
