package net.hypixel.nerdbot.app.role;

import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleIdSyncTest {

    @Test
    void setsRolesOnUserWithNullRoleIds() {
        DiscordUser user = new DiscordUser();
        user.setDiscordId("123");
        assertTrue(RoleIdSync.applyRoleIds(user, List.of("100", "200")), "null role ids should count as a change");
        assertEquals(List.of("100", "200"), user.getRoleIds());
    }

    @Test
    void reportsNoChangeWhenRolesAreIdentical() {
        DiscordUser user = new DiscordUser("123");
        user.setRoleIds(List.of("100", "200"));
        assertFalse(RoleIdSync.applyRoleIds(user, List.of("100", "200")));
    }

    @Test
    void replacesRolesWhenTheyDiffer() {
        DiscordUser user = new DiscordUser("123");
        user.setRoleIds(List.of("100"));
        assertTrue(RoleIdSync.applyRoleIds(user, List.of("100", "300")));
        assertEquals(List.of("100", "300"), user.getRoleIds());
    }

    @Test
    void clearsRolesWhenMemberHasNone() {
        DiscordUser user = new DiscordUser("123");
        user.setRoleIds(List.of("100"));
        assertTrue(RoleIdSync.applyRoleIds(user, List.of()));
        assertEquals(List.of(), user.getRoleIds());
    }
}
