package net.hypixel.nerdbot.app.profile;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.storage.MissingUserRecordException;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileLinkerTest {

    private static final String MEMBER = "100";
    private static final String NEW_MEMBER_ROLE = "1";
    private static final String LIMBO_ROLE = "2";

    private final CallRecorder recorder = new CallRecorder();
    private final NerdBotConfig config = new NerdBotConfig();
    private final FakeDiscordUserStore users = new FakeDiscordUserStore();
    private final ProfileLinker linker = new ProfileLinker(users, () -> config);
    private final MojangProfile profile = new MojangProfile();

    private final Role newMemberRole = role(NEW_MEMBER_ROLE, 5);
    private final Role limboRole = role(LIMBO_ROLE, 1);
    private final Map<String, Role> guildRoles = new HashMap<>(Map.of(NEW_MEMBER_ROLE, newMemberRole, LIMBO_ROLE, limboRole));
    private final List<String> roleChanges = new ArrayList<>();
    private boolean roleChangesSucceed = true;
    private boolean canRename = true;

    @BeforeEach
    void setUp() {
        profile.setUsername("Notch");
        profile.setUniqueId(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"));
        config.getRoleConfig().setNewMemberRoleId(NEW_MEMBER_ROLE);
        config.getRoleConfig().setLimboRoleId(LIMBO_ROLE);
        users.seed(new DiscordUser(MEMBER));
    }

    @Test
    void theProfileIsSavedAndTheMemberRenamedAndPromoted() {
        linker.link(member("someone", List.of(limboRole)), profile).join();

        assertSame(profile, users.findById(MEMBER).orElseThrow().getMojangProfile());
        assertEquals(List.of(MEMBER), users.savedIds());
        assertEquals("Notch", recorder.last("modifyNickname").arg(0));
        assertEquals(List.of("add " + NEW_MEMBER_ROLE, "remove " + LIMBO_ROLE), roleChanges);
    }

    @Test
    void aNicknameThatAlreadyHasTheNameIsKept() {
        linker.link(member("[Mod] notch", List.of()), profile).join();

        assertFalse(recorder.called("modifyNickname"));
    }

    @Test
    void membersAlreadyAtOrAboveTheNewMemberRoleKeepTheirRoles() {
        linker.link(member("Notch", List.of(role("9", 5))), profile).join();

        assertEquals(List.of(), roleChanges);
    }

    @Test
    void limboIsOnlyLeftOnceTheNewMemberRoleIsGiven() {
        roleChangesSucceed = false;

        linker.link(member("Notch", List.of()), profile).join();

        assertEquals(List.of("add " + NEW_MEMBER_ROLE), roleChanges);
    }

    @Test
    void aMissingNewMemberRoleSkipsTheRoles() {
        guildRoles.remove(NEW_MEMBER_ROLE);

        linker.link(member("Notch", List.of()), profile).join();

        assertEquals(List.of(), roleChanges);
    }

    @Test
    void noConfiguredNewMemberRoleSkipsTheRoles() {
        config.getRoleConfig().setNewMemberRoleId("");

        linker.link(member("Notch", List.of()), profile).join();

        assertEquals(List.of(), roleChanges);
    }

    @Test
    void noConfiguredLimboRoleStillGivesTheNewMemberRole() {
        config.getRoleConfig().setLimboRoleId(null);

        linker.link(member("Notch", List.of()), profile).join();

        assertEquals(List.of("add " + NEW_MEMBER_ROLE), roleChanges);
    }

    @Test
    void aRenameDiscordRefusesDoesNotFailTheLink() {
        canRename = false;

        linker.link(member("someone", List.of()), profile).join();

        assertEquals(List.of(MEMBER), users.savedIds());
        assertEquals(List.of("add " + NEW_MEMBER_ROLE, "remove " + LIMBO_ROLE), roleChanges);
    }

    @Test
    void withoutARecordNothingChanges() {
        CompletionException thrown = assertThrows(CompletionException.class, () -> linker.link(member("someone", List.of(), "999"), profile).join());

        assertInstanceOf(MissingUserRecordException.class, thrown.getCause());
        assertFalse(recorder.called("modifyNickname"));
        assertEquals(List.of(), roleChanges);
    }

    @Test
    void aFailedSaveStopsTheRenameAndRoles() {
        users.failSavesFor(MEMBER);

        assertThrows(CompletionException.class, () -> linker.link(member("someone", List.of()), profile).join());

        assertFalse(recorder.called("modifyNickname"));
        assertEquals(List.of(), roleChanges);
    }

    private Member member(String effectiveName, List<Role> roles) {
        return member(effectiveName, roles, MEMBER);
    }

    private Member member(String effectiveName, List<Role> roles, String id) {
        Guild guild = Fakes.of(Guild.class, Map.of(
            "getRoleById", (CallRecorder.Answer) args -> guildRoles.get((String) args[0]),
            "addRoleToMember", (CallRecorder.Answer) args -> roleChange("add " + ((Role) args[1]).getId()),
            "removeRoleFromMember", (CallRecorder.Answer) args -> roleChange("remove " + ((Role) args[1]).getId())));
        return recorder.fake(Member.class, Map.of(
            "getId", id,
            "getEffectiveName", effectiveName,
            "getRoles", roles,
            "getGuild", guild,
            "getUser", Fakes.of(User.class, Map.of("getId", id)),
            "modifyNickname", (CallRecorder.Answer) args -> {
                if (!canRename) {
                    throw new HierarchyException("Can't modify a member with higher or equal highest role than yourself!");
                }
                return recorder.fake(AuditableRestAction.class);
            }));
    }

    private AuditableRestAction<?> roleChange(String change) {
        return Fakes.of(AuditableRestAction.class, Map.of("queue", (CallRecorder.Answer) args -> {
            roleChanges.add(change);
            if (roleChangesSucceed && args.length > 0 && args[0] != null) {
                consumer(args[0]).accept(null);
            } else if (!roleChangesSucceed && args.length > 1 && args[1] != null) {
                consumer(args[1]).accept(new IllegalStateException("missing permission"));
            }
            return null;
        }));
    }

    private static Role role(String id, int position) {
        return Fakes.of(Role.class, Map.of("getId", id, "getPosition", position));
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object> consumer(Object value) {
        return (Consumer<Object>) value;
    }
}
