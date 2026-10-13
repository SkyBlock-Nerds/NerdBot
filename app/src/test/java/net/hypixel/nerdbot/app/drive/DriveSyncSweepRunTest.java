package net.hypixel.nerdbot.app.drive;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.config.objects.DriveFolderMapping;
import net.hypixel.nerdbot.app.listener.GuildScope;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.app.testsupport.MemberFakes;
import net.hypixel.nerdbot.app.user.MemberQueue;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.security.AesGcmCipher;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.hypixel.nerdbot.app.testsupport.MemberFakes.MAIN_GUILD_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sweep as the hourly feature and {@code /drive sync} run it, over the stored members. */
class DriveSyncSweepRunTest {

    private static final AesGcmCipher CIPHER = new AesGcmCipher("0123456789abcdef0123456789abcdef".getBytes());

    private final NerdBotConfig config = new NerdBotConfig();
    private final FakeDiscordUserStore users = new FakeDiscordUserStore();
    private final FakeDrivePermissionClient client = new FakeDrivePermissionClient();
    private final DrivePermissionService service = new DrivePermissionService(CIPHER, client);
    private final List<MessageEmbed> logged = new ArrayList<>();
    private final List<String> roles = new ArrayList<>();
    private Optional<DrivePermissionService> drive = Optional.of(service);
    private List<Guild> guilds;

    private final DriveSyncSweep sweep = new DriveSyncSweep(users, new MemberQueue(users, Runnable::run), () -> drive, () -> config,
        new GuildScope(() -> config), Fakes.of(JDA.class, Map.of("getGuilds", (CallRecorder.Answer) args -> guilds)), logged::add);

    @BeforeEach
    void setUp() {
        config.setGuildId(MAIN_GUILD_ID);
        config.getGoogleDriveConfig().setEnabled(true);
        DriveFolderMapping mapping = new DriveFolderMapping();
        mapping.setRoleId("111");
        mapping.setFolderIds(List.of("fA"));
        mapping.setAccessLevel("READER");
        config.getGoogleDriveConfig().setFolderMappings(List.of(mapping));

        Guild guild = Fakes.of(Guild.class, Map.of("getId", MAIN_GUILD_ID,
            "retrieveMemberById", (CallRecorder.Answer) args -> Fakes.of(CacheRestAction.class, Map.of(
                "complete", MemberFakes.member(String.valueOf(args[0]), false, roles.stream().map(MemberFakes::role).toList())))));
        guilds = List.of(guild);
    }

    private DiscordUser linkedUser(String id) {
        DiscordUser user = new DiscordUser(id);
        new DriveLinkWorkflow(service, config.getGoogleDriveConfig()).link(user, id + "@example.com", List.of(), List.of(user));
        users.seed(user);
        return user;
    }

    @Test
    void storedLinkedMembersAreSyncedSavedAndLogged() {
        DiscordUser user = linkedUser("1");
        users.seed(new DiscordUser("2"));
        roles.add("111");

        DriveSyncSweep.Result result = sweep.run();

        assertEquals(new DriveSyncSweep.Result(1, 0, 0), result);
        assertEquals(1, user.getDriveAccess().getGrants().size());
        assertEquals(List.of("1"), users.savedIds());
        assertEquals(1, logged.size());
    }

    @Test
    void withDriveOffNothingHappens() {
        linkedUser("1");
        config.getGoogleDriveConfig().setEnabled(false);

        assertEquals(new DriveSyncSweep.Result(0, 0, 0), sweep.run());
        assertTrue(users.savedIds().isEmpty());
    }

    @Test
    void withoutADriveServiceNothingHappens() {
        linkedUser("1");
        drive = Optional.empty();

        assertEquals(new DriveSyncSweep.Result(0, 0, 0), sweep.run());
    }

    @Test
    void withoutTheGuildNothingHappens() {
        linkedUser("1");
        guilds = List.of();

        assertEquals(new DriveSyncSweep.Result(0, 0, 0), sweep.run());
        assertTrue(users.savedIds().isEmpty());
    }

    @Test
    void aMemberWhoseLookupFailsIsCountedWithoutStoppingTheRest() {
        linkedUser("1");
        linkedUser("2");
        roles.add("111");
        users.throwOnFindFor("1");

        DriveSyncSweep.Result result = sweep.run();

        assertEquals(1, result.failed());
        assertEquals(1, result.synced());
    }
}
