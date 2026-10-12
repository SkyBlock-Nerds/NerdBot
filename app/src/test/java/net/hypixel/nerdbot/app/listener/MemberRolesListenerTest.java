package net.hypixel.nerdbot.app.listener;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static net.hypixel.nerdbot.app.listener.MemberListenerFixture.DRIVE_FOLDER;
import static net.hypixel.nerdbot.app.listener.MemberListenerFixture.DRIVE_ROLE;
import static net.hypixel.nerdbot.app.testsupport.MemberFakes.member;
import static net.hypixel.nerdbot.app.testsupport.MemberFakes.role;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberRolesListenerTest {

    private final MemberListenerFixture fixture = new MemberListenerFixture();
    private final MemberRolesListener listener = new MemberRolesListener(fixture.guildScope, fixture.queue, fixture.users,
        () -> fixture.config, () -> fixture.drive, fixture.logChannel);

    @Test
    void newRolesAreStoredAndSaved() {
        fixture.users.seed(new DiscordUser("1"));

        listener.sync(member("1", role("10"))).join();

        assertEquals(List.of("10"), fixture.users.findById("1").orElseThrow().getRoleIds());
        assertEquals(List.of("1"), fixture.users.savedIds());
    }

    @Test
    void unchangedRolesAreNotSaved() {
        DiscordUser user = new DiscordUser("1");
        user.setRoleIds(List.of("10"));
        fixture.users.seed(user);

        listener.sync(member("1", role("10"))).join();

        assertTrue(fixture.users.savedIds().isEmpty());
    }

    @Test
    void aMemberWithoutARecordIsLeftWithoutOne() {
        listener.sync(member("1", role("10"))).join();

        assertEquals(Optional.empty(), fixture.users.findById("1"));
        assertTrue(fixture.users.savedIds().isEmpty());
    }

    @Test
    void theRolesHeldWhenTheChangeRunsAreTheOnesStored() {
        fixture.users.seed(new DiscordUser("1"));
        List<Role> roles = new ArrayList<>(List.of(role("10")));
        Member member = member("1", false, roles);
        roles.add(role("11"));

        listener.sync(member).join();

        assertEquals(List.of("10", "11"), fixture.users.findById("1").orElseThrow().getRoleIds());
    }

    @Test
    void gainingAMappedRoleGrantsItsDriveFolder() {
        fixture.linkedUser("1", List.of());

        listener.sync(member("1", role(DRIVE_ROLE))).join();

        DiscordUser user = fixture.users.findById("1").orElseThrow();
        assertEquals(DRIVE_FOLDER, user.getDriveAccess().getGrants().getFirst().folderId());
        assertEquals(1, fixture.driveClient.folders.get(DRIVE_FOLDER).size());
        assertEquals(List.of("1"), fixture.users.savedIds());
        assertEquals(List.of("User", "User ID", "Granted"), fixture.fieldNames(fixture.logged.getFirst()));
    }

    @Test
    void losingAMappedRoleRevokesItsDriveFolder() {
        DiscordUser user = fixture.linkedUser("1", List.of(DRIVE_ROLE));

        listener.sync(member("1")).join();

        assertTrue(user.getDriveAccess().getGrants().isEmpty());
        assertTrue(fixture.driveClient.folders.get(DRIVE_FOLDER).isEmpty());
        assertEquals(List.of("User", "User ID", "Revoked"), fixture.fieldNames(fixture.logged.getFirst()));
    }

    @Test
    void anUnlinkedMemberOnlyHasTheirRolesUpdated() {
        fixture.users.seed(new DiscordUser("1"));

        listener.sync(member("1", role(DRIVE_ROLE))).join();

        assertNull(fixture.users.findById("1").orElseThrow().getDriveAccess());
        assertTrue(fixture.driveClient.folders.isEmpty());
        assertTrue(fixture.logged.isEmpty());
    }

    @Test
    void withDriveDisabledFoldersAreLeftAlone() {
        fixture.linkedUser("1", List.of());
        fixture.config.getGoogleDriveConfig().setEnabled(false);

        listener.sync(member("1", role(DRIVE_ROLE))).join();

        assertTrue(fixture.driveClient.folders.isEmpty());
        assertEquals(List.of(DRIVE_ROLE), fixture.users.findById("1").orElseThrow().getRoleIds());
    }
}
