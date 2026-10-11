package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.hypixel.nerdbot.app.command.RoleCommands.PromotionStanding;
import net.hypixel.nerdbot.app.command.RoleCommands.RoleMenuView;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.app.testsupport.FakeDiscordUserStore;
import net.hypixel.nerdbot.discord.config.RoleConfig;
import net.hypixel.nerdbot.discord.config.objects.PingableRole;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleCommandsTest {

    private static final String MEMBER_ID = "100";
    private static final String NEW_MEMBER_ROLE = "10";
    private static final String MEMBER_ROLE = "11";
    private static final String ORANGE_ROLE = "12";
    private static final String EVENTS_ROLE = "20";
    private static final String GIVEAWAYS_ROLE = "21";
    private static final String DELETED_ROLE = "22";

    private final CallRecorder recorder = new CallRecorder();
    private final NerdBotConfig config = new NerdBotConfig();
    private final RoleConfig roleConfig = config.getRoleConfig();
    private final FakeDiscordUserStore users = new FakeDiscordUserStore();
    private final RoleCommands commands = new RoleCommands(() -> config, users);
    private boolean roleChangesSucceed = true;

    @BeforeEach
    void setUp() {
        roleConfig.setPingableRoles(new PingableRole[]{
            new PingableRole("Events", EVENTS_ROLE),
            new PingableRole("Giveaways", GIVEAWAYS_ROLE),
            new PingableRole("Deleted", DELETED_ROLE)});
        roleConfig.setMemberRoleId(MEMBER_ROLE);
        roleConfig.setPromotionTierRoleIds(new String[]{NEW_MEMBER_ROLE, MEMBER_ROLE, ORANGE_ROLE});
        roleConfig.setCurrentlyPromotingUsers(true);
        roleConfig.setMinimumVotesRequiredForPromotion(2);
        roleConfig.setMinimumCommentsRequiredForPromotion(5);
    }

    @AfterEach
    void neverBlocksOnARestAction() {
        assertFalse(recorder.called("complete"), "handlers must queue, not complete");
    }

    @Nested
    class Menu {

        @Test
        void onlyRolesThatExistAreOfferedAndHeldOnesAreTicked() {
            RoleMenuView view = RoleCommands.roleMenu(MEMBER_ID, pingableRoles(), Set.of(EVENTS_ROLE), id -> !id.equals(DELETED_ROLE), null);

            StringSelectMenu menu = view.menu().orElseThrow();
            assertEquals("role-select-" + MEMBER_ID, menu.getId());
            assertEquals(List.of("✅ Events", "❌ Giveaways"), menu.getOptions().stream().map(SelectOption::getLabel).toList());
            assertTrue(view.text().startsWith("**Your Current Roles:** Events"));
            assertTrue(view.text().contains("**Available Roles:** 3"));
        }

        @Test
        void aMessageGoesAboveTheStatus() {
            RoleMenuView view = RoleCommands.roleMenu(MEMBER_ID, pingableRoles(), Set.of(), id -> true, "✅ Added role");

            assertTrue(view.text().startsWith("✅ Added role\n\n**Your Current Roles:** None"));
        }

        @Test
        void noMenuWhenNoConfiguredRoleExists() {
            assertTrue(RoleCommands.roleMenu(MEMBER_ID, pingableRoles(), Set.of(), id -> false, null).menu().isEmpty());
        }

        @Test
        void showingTheMenu() {
            commands.showRoleMenu(slash(member(EVENTS_ROLE)));

            assertTrue(DiscordFakes.lastEditText(recorder).startsWith("**Your Current Roles:** Events"));
            assertEquals(1, DiscordFakes.lastComponents(recorder).size());
        }

        @Test
        void withNoRolesToOfferItSaysSo() {
            roleConfig.setPingableRoles(new PingableRole[0]);

            commands.showRoleMenu(slash(member()));

            assertEquals("No roles are currently available.", DiscordFakes.lastEditText(recorder));
        }
    }

    @Nested
    class Toggle {

        @Test
        void addingARoleShowsItTickedEvenBeforeDiscordUpdatesTheMember() {
            commands.toggleRole(select(member(), "Events"));

            assertTrue(recorder.called("addRoleToMember"));
            assertTrue(DiscordFakes.lastEditText(recorder).startsWith("✅ Added role: <@&" + EVENTS_ROLE + ">"));
            assertTrue(DiscordFakes.lastEditText(recorder).contains("**Your Current Roles:** Events"));
            assertEquals("✅ Events", DiscordFakes.selectMenu(DiscordFakes.lastComponents(recorder).getFirst()).getOptions().getFirst().getLabel());
        }

        @Test
        void removingARoleShowsItUnticked() {
            commands.toggleRole(select(member(EVENTS_ROLE), "events"));

            assertTrue(recorder.called("removeRoleFromMember"));
            assertTrue(DiscordFakes.lastEditText(recorder).contains("**Your Current Roles:** None"));
        }

        @Test
        void aFailedChangeIsReportedPrivately() {
            roleChangesSucceed = false;

            commands.toggleRole(select(member(), "Events"));

            assertEquals("❌ Failed to add role: Events", recorder.last("sendMessage").arg(0));
            assertFalse(recorder.called("editOriginal"));
        }

        @Test
        void anUnknownRoleNameIsReported() {
            commands.toggleRole(select(member(), "Nope"));

            assertEquals("❌ Role not found: Nope", recorder.last("sendMessage").arg(0));
        }

        @Test
        void aConfiguredRoleMissingFromTheServerIsReported() {
            commands.toggleRole(select(member(), "Deleted"));

            assertEquals("❌ Could not find role: Deleted", recorder.last("sendMessage").arg(0));
        }

        @Test
        void someoneElseCannotUseTheMenu() {
            Map<String, Object> answers = new HashMap<>(interactionAnswers("999", member()));
            answers.put("getComponentId", "role-select-" + MEMBER_ID);
            answers.put("getValues", List.of("Events"));

            commands.toggleRole(recorder.fake(StringSelectInteraction.class, answers));

            assertEquals(RoleCommands.NOT_YOUR_MENU, recorder.last("reply").arg(0));
            assertFalse(recorder.called("addRoleToMember"));
        }
    }

    @Nested
    class Promotion {

        @Test
        void standingFollowsThePromotionPath() {
            assertEquals(PromotionStanding.MEMBER, RoleCommands.promotionStanding(List.of(MEMBER_ROLE), roleConfig));
            assertEquals(PromotionStanding.ABOVE_MEMBER, RoleCommands.promotionStanding(List.of(ORANGE_ROLE, MEMBER_ROLE), roleConfig));
            assertEquals(PromotionStanding.ABOVE_MEMBER, RoleCommands.promotionStanding(List.of(ORANGE_ROLE), roleConfig));
            assertEquals(PromotionStanding.BELOW_MEMBER, RoleCommands.promotionStanding(List.of(NEW_MEMBER_ROLE), roleConfig));
            assertEquals(PromotionStanding.BELOW_MEMBER, RoleCommands.promotionStanding(List.of(), roleConfig));
            assertEquals(PromotionStanding.MEMBER, RoleCommands.promotionStanding(List.of(EVENTS_ROLE, MEMBER_ROLE), roleConfig), "ping roles don't count");
        }

        @Test
        void withoutThePathItFallsBackToTheHighestRole() {
            roleConfig.setPromotionTierRoleIds(new String[0]);

            assertEquals(PromotionStanding.MEMBER, RoleCommands.promotionStanding(List.of(MEMBER_ROLE), roleConfig));
            assertEquals(PromotionStanding.ABOVE_MEMBER, RoleCommands.promotionStanding(List.of(ORANGE_ROLE, MEMBER_ROLE), roleConfig));
            assertEquals(PromotionStanding.BELOW_MEMBER, RoleCommands.promotionStanding(List.of(NEW_MEMBER_ROLE), roleConfig));
        }

        @Test
        void notPromotingSaysSo() {
            roleConfig.setCurrentlyPromotingUsers(false);

            commands.checkPromotion(slash(member(MEMBER_ROLE)));

            assertEquals("We are not currently assessing promotion eligibility, please check back later!", DiscordFakes.lastEditText(recorder));
        }

        @Test
        void anUnsetMemberRoleIsReportedNotCrashedOn() {
            roleConfig.setMemberRoleId("");

            commands.checkPromotion(slash(member(MEMBER_ROLE)));

            assertEquals("Promotion checks aren't set up yet, please check back later!", DiscordFakes.lastEditText(recorder));
        }

        @Test
        void newMembersAreToldWhenChecksStart() {
            commands.checkPromotion(slash(member(NEW_MEMBER_ROLE)));

            assertEquals("Promotion checks start once you have the Member role.", DiscordFakes.lastEditText(recorder));
        }

        @Test
        void higherRolesAreAlreadyAtTheTop() {
            commands.checkPromotion(slash(member(ORANGE_ROLE, MEMBER_ROLE)));

            assertEquals("You are already at the highest role!", DiscordFakes.lastEditText(recorder));
        }

        @Test
        void membersShortOfVotesSeeTheirProgress() {
            DiscordUser user = new DiscordUser(MEMBER_ID);
            user.getLastActivity().getSuggestionVoteHistoryMap().put("a", System.currentTimeMillis());
            users.seed(user);

            commands.checkPromotion(slash(member(MEMBER_ROLE)));

            assertEquals("You have 1/2 required suggestion votes and 0/5 required suggestion comments to be nominated for the Orange role!",
                DiscordFakes.lastEditText(recorder));
        }

        @Test
        void enoughVotesIsEligibleWhateverTheComments() {
            DiscordUser user = new DiscordUser(MEMBER_ID);
            user.getLastActivity().getSuggestionVoteHistoryMap().put("a", System.currentTimeMillis());
            user.getLastActivity().getSuggestionVoteHistoryMap().put("b", System.currentTimeMillis());
            users.seed(user);

            commands.checkPromotion(slash(member(MEMBER_ROLE)));

            assertTrue(DiscordFakes.lastEditText(recorder).startsWith("You are currently eligible for a promotion!"));
        }

        @Test
        void aMemberWithoutARecordIsLeftToTheCommandLibrary() {
            assertThrows(IllegalStateException.class, () -> commands.checkPromotion(slash(member(MEMBER_ROLE))));
        }
    }

    private List<PingableRole> pingableRoles() {
        return Arrays.asList(roleConfig.getPingableRoles());
    }

    private static Role role(String id) {
        return Fakes.of(Role.class, Map.of("getId", id, "getAsMention", "<@&" + id + ">"));
    }

    private static Member member(String... roleIds) {
        return Fakes.of(Member.class, Map.of("getId", MEMBER_ID, "getRoles", Arrays.stream(roleIds).map(RoleCommandsTest::role).toList()));
    }

    private Guild guild() {
        CallRecorder.Answer roleChange = args -> recorder.fake(AuditableRestAction.class, Map.of("queue", (CallRecorder.Answer) queueArgs -> {
            if (roleChangesSucceed && queueArgs.length > 0 && queueArgs[0] != null) {
                consumer(queueArgs[0]).accept(null);
            } else if (!roleChangesSucceed && queueArgs.length > 1 && queueArgs[1] != null) {
                consumer(queueArgs[1]).accept(new IllegalStateException("missing permission"));
            }
            return null;
        }));

        return recorder.fake(Guild.class, Map.of(
            "getRoleById", (CallRecorder.Answer) args -> DELETED_ROLE.equals(args[0]) ? null : role((String) args[0]),
            "addRoleToMember", roleChange,
            "removeRoleFromMember", roleChange));
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object> consumer(Object value) {
        return (Consumer<Object>) value;
    }

    private Map<String, Object> interactionAnswers(String userId, Member member) {
        return Map.of("getUser", DiscordFakes.user(userId), "getMember", member, "getGuild", guild());
    }

    private SlashCommandInteraction slash(Member member) {
        return recorder.fake(SlashCommandInteraction.class, interactionAnswers(MEMBER_ID, member));
    }

    private StringSelectInteraction select(Member member, String value) {
        Map<String, Object> answers = new HashMap<>(interactionAnswers(MEMBER_ID, member));
        answers.put("getComponentId", "role-select-" + MEMBER_ID);
        answers.put("getValues", List.of(value));
        return recorder.fake(StringSelectInteraction.class, answers);
    }
}
