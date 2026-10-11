package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.app.role.SpecialRoles;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import net.hypixel.nerdbot.marmalade.storage.database.model.greenlit.GreenlitMessage;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfoCommandsTest {

    private final CallRecorder recorder = new CallRecorder();
    private final NerdBotConfig config = new NerdBotConfig();
    private final JDA jda = Fakes.of(JDA.class, Map.of("getSelfUser", Fakes.of(SelfUser.class, Map.of("getName", "NerdBot", "getId", "1"))));
    private CompletableFuture<List<GreenlitMessage>> greenlit = CompletableFuture.completedFuture(List.of());
    private final InfoCommands commands = new InfoCommands(jda, () -> config, () -> greenlit);

    @Test
    void botInfoNamesTheBotPrivately() {
        commands.showBotInfo(DiscordFakes.slash(recorder, "1"));

        assertTrue(((String) recorder.last("reply").arg(0)).startsWith("- Bot name: NerdBot (ID: 1)"));
        assertEquals(true, recorder.last("setEphemeral").arg(0));
    }

    @Test
    void greenlitWithNothingSaysSo() {
        commands.showGreenlit(DiscordFakes.slash(recorder, "1"));

        assertEquals("No greenlit suggestions found!", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void aGreenlitFailureDoesNotShowTheExceptionText() {
        greenlit = CompletableFuture.failedFuture(new IllegalStateException("GreenlitMessageRepository is not registered; is the database connected?"));

        commands.showGreenlit(DiscordFakes.slash(recorder, "1"));

        assertEquals("Failed to load greenlit suggestions. Please try again later.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void greenlitIsNewestFirst() {
        GreenlitMessage older = greenlitAt(1_000);
        GreenlitMessage newer = greenlitAt(2_000);

        assertEquals(List.of(newer, older), InfoCommands.newestFirst(new ArrayList<>(List.of(older, newer))));
    }

    @Test
    void serverInfoCountsStaffGrapesAndNerds() {
        config.getRoleConfig().setModeratorRoleId("30");
        config.getRoleConfig().setOrangeRoleId("31");
        Role staffRole = Fakes.of(Role.class, Map.of("getId", "20"));
        Role grape = Fakes.of(Role.class, Map.of("getId", "30"));
        Role orange = Fakes.of(Role.class, Map.of("getId", "31"));

        Guild guild = guild(Map.of(
            "getRolesByName", (CallRecorder.Answer) args -> args[0].equals(SpecialRoles.NAMES[0]) ? List.of(staffRole) : List.of(),
            "getRoleById", (CallRecorder.Answer) args -> args[0].equals("30") ? grape : args[0].equals("31") ? orange : null,
            "getMembersWithRoles", (CallRecorder.Answer) args -> {
                Object role = ((Object[]) args[0])[0];
                int count = role == staffRole ? 2 : role == grape ? 3 : 4;
                return new ArrayList<>(java.util.Collections.nCopies(count, new Object()));
            }));

        commands.showServerInfo(slashIn(guild));

        String reply = (String) recorder.last("reply").arg(0);
        assertTrue(reply.contains("- Staff: 2\n"));
        assertTrue(reply.contains("- Grapes: 3\n"));
        assertTrue(reply.contains("- Nerds: 4\n"));
    }

    @Test
    void unsetOrMalformedRoleIdsCountAsNobody() {
        config.getRoleConfig().setModeratorRoleId("");
        config.getRoleConfig().setOrangeRoleId("not-an-id");

        Guild guild = guild(Map.of(
            "getRolesByName", (CallRecorder.Answer) args -> List.of(),
            "getRoleById", (CallRecorder.Answer) args -> {
                throw new NumberFormatException("not a snowflake");
            }));

        commands.showServerInfo(slashIn(guild));

        String reply = (String) recorder.last("reply").arg(0);
        assertTrue(reply.contains("- Grapes: 0\n"));
        assertTrue(reply.contains("- Nerds: 0\n"));
    }

    private Guild guild(Map<String, Object> roleAnswers) {
        Map<String, Object> answers = new java.util.HashMap<>(roleAnswers);
        answers.put("getName", "SkyBlock Nerds");
        answers.put("getId", "9");
        answers.put("getTimeCreated", OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        answers.put("getBoostTier", Guild.BoostTier.TIER_1);
        return recorder.fake(Guild.class, answers);
    }

    private SlashCommandInteraction slashIn(Guild guild) {
        return recorder.fake(SlashCommandInteraction.class, Map.of("getUser", DiscordFakes.user("1"), "getGuild", guild));
    }

    private static GreenlitMessage greenlitAt(long timestamp) {
        GreenlitMessage message = new GreenlitMessage();
        message.setSuggestionTimestamp(timestamp);
        return message;
    }
}
