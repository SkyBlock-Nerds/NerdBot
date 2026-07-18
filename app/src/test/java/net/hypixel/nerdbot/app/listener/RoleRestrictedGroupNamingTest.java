package net.hypixel.nerdbot.app.listener;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How a role-restricted channel group derives its stable identifier and human-readable display name
 * from the names of the roles that gate it.
 */
class RoleRestrictedGroupNamingTest {

    @Test
    void identifierLowercasesStripsAndSortsRoleNames() {
        // "Admin Team!" -> "adminteam", "Mod" -> "mod", sorted for a deterministic identifier
        assertEquals(
            "adminteam-mod-channels",
            RoleRestrictedChannelListener.formatGroupIdentifier(List.of("Mod", "Admin Team!"), Set.of())
        );
    }

    @Test
    void identifierFallsBackToRoleIdsWhenNoNamesResolve() {
        assertEquals(
            "999-channels",
            RoleRestrictedChannelListener.formatGroupIdentifier(List.of(), Set.of("999"))
        );
    }

    @Test
    void displayNameForNoRolesIsUnknown() {
        assertEquals("Unknown Role Channels", RoleRestrictedChannelListener.formatGroupDisplayName(List.of()));
    }

    @Test
    void displayNameForSingleRole() {
        assertEquals("Admin Channels", RoleRestrictedChannelListener.formatGroupDisplayName(List.of("Admin")));
    }

    @Test
    void displayNameForTwoRolesSortsAndJoinsWithAmpersand() {
        assertEquals(
            "Admin & Mod Channels",
            RoleRestrictedChannelListener.formatGroupDisplayName(List.of("Mod", "Admin"))
        );
    }

    @Test
    void displayNameForThreeOrMoreRolesUsesCommasThenAmpersand() {
        assertEquals(
            "Admin, Mod & VIP Channels",
            RoleRestrictedChannelListener.formatGroupDisplayName(List.of("VIP", "Admin", "Mod"))
        );
    }
}
