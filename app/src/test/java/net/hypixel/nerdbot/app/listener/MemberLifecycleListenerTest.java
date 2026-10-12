package net.hypixel.nerdbot.app.listener;

import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static net.hypixel.nerdbot.app.listener.MemberListenerFixture.DRIVE_FOLDER;
import static net.hypixel.nerdbot.app.listener.MemberListenerFixture.DRIVE_ROLE;
import static net.hypixel.nerdbot.app.testsupport.MemberFakes.member;
import static net.hypixel.nerdbot.app.testsupport.MemberFakes.role;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberLifecycleListenerTest {

    private final MemberListenerFixture fixture = new MemberListenerFixture();
    private final MemberLifecycleListener listener = new MemberLifecycleListener(fixture.guildScope, fixture.queue, fixture.users,
        () -> fixture.config, () -> fixture.drive, fixture.logChannel);

    @Test
    void joiningCreatesAndSavesARecordWithTheMembersRoles() {
        listener.join(member("1", role("10"), role("11"))).join();

        DiscordUser created = fixture.users.findById("1").orElseThrow();
        assertEquals(List.of("10", "11"), created.getRoleIds());
        assertEquals(List.of("1"), fixture.users.savedIds());
    }

    @Test
    void rejoiningKeepsTheExistingRecord() {
        DiscordUser existing = new DiscordUser("1");
        fixture.users.seed(existing);

        listener.join(member("1")).join();

        assertSame(existing, fixture.users.findById("1").orElseThrow());
        assertTrue(fixture.users.savedIds().isEmpty());
    }

    @Test
    void leavingRemovesTheRecord() {
        fixture.users.seed(new DiscordUser("1"));

        listener.leave("1").join();

        assertEquals(Optional.empty(), fixture.users.findById("1"));
        assertEquals(List.of("1"), fixture.users.deletedIds());
        assertTrue(fixture.logged.isEmpty());
    }

    @Test
    void leavingRevokesDriveAccessBeforeTheRecordGoes() {
        fixture.linkedUser("1", List.of(DRIVE_ROLE));
        assertEquals(1, fixture.driveClient.folders.get(DRIVE_FOLDER).size());

        listener.leave("1").join();

        assertTrue(fixture.driveClient.folders.get(DRIVE_FOLDER).isEmpty());
        assertEquals(List.of("1"), fixture.users.deletedIds());
        assertEquals(1, fixture.logged.size());
        assertEquals(List.of("User", "User ID", "Revoked"), fixture.fieldNames(fixture.logged.getFirst()));
    }

    @Test
    void withDriveDisabledLeavingLeavesDriveAlone() {
        fixture.linkedUser("1", List.of(DRIVE_ROLE));
        fixture.config.getGoogleDriveConfig().setEnabled(false);

        listener.leave("1").join();

        assertEquals(1, fixture.driveClient.folders.get(DRIVE_FOLDER).size());
        assertEquals(List.of("1"), fixture.users.deletedIds());
    }

    @Test
    void withoutADriveServiceLeavingStillRemovesTheRecord() {
        fixture.linkedUser("1", List.of(DRIVE_ROLE));
        fixture.drive = Optional.empty();

        listener.leave("1").join();

        assertEquals(List.of("1"), fixture.users.deletedIds());
    }

    @Test
    void leavingWithoutARecordIsHarmless() {
        listener.leave("1").join();

        assertEquals(List.of("1"), fixture.users.deletedIds());
    }

    @Test
    void aJoinQueuedBeforeALeaveIsUndoneByIt() {
        listener.join(member("1"));
        listener.leave("1").join();

        assertEquals(Optional.empty(), fixture.users.findById("1"));
    }
}
