package net.hypixel.nerdbot.app.command;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import net.dv8tion.jda.api.requests.restaction.InviteAction;
import net.hypixel.nerdbot.app.testsupport.DiscordFakes;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InviteCommandsTest {

    private final CallRecorder recorder = new CallRecorder();
    private final List<MessageEmbed> logged = new ArrayList<>();
    private final InviteCommands commands = new InviteCommands(logged::add);
    private final AtomicInteger created = new AtomicInteger();
    private int failEvery;
    private boolean canCreate = true;
    private List<Invite> existing = List.of();
    private int deleteFailures;

    @AfterEach
    void neverBlocksOnARestAction() {
        assertFalse(recorder.called("complete"), "handlers must queue, not complete");
    }

    @Test
    void invitesAreCreatedAndListed() {
        commands.create(slash(), 3, channel("limbo")).join();

        String reply = DiscordFakes.lastEditText(recorder);
        assertTrue(reply.startsWith("**Created 3 invites:**\nhttps://discord.gg/1\n"), reply);
        assertEquals(1, logged.size());
        assertTrue(logged.getFirst().getDescription().contains("created 3 invite(s)"));
    }

    @Test
    void theLimboChannelIsUsedWhenNoneIsGiven() {
        commands.create(slash(), 1, null).join();

        assertTrue(DiscordFakes.lastEditText(recorder).startsWith("**Created 1 invites:**"));
    }

    @Test
    void withoutALimboChannelOneMustBePicked() {
        commands.create(slash(List.of()), 1, null).join();

        assertEquals("There's no #limbo channel to default to, so please pick a channel.", recorder.last("reply").arg(0));
    }

    @Test
    void amountsOutsideTheLimitAreRefused() {
        commands.create(slash(), 0, channel("limbo")).join();
        assertEquals("You can create between 1 and 25 invites at a time.", recorder.last("reply").arg(0));

        commands.create(slash(), 26, channel("limbo")).join();
        assertEquals(0, created.get());
    }

    @Test
    void invitesThatFailAreReportedAndOnlyRealOnesLogged() {
        failEvery = 2;

        commands.create(slash(), 4, channel("limbo")).join();

        String reply = DiscordFakes.lastEditText(recorder);
        assertTrue(reply.startsWith("**Created 2 invites:**"), reply);
        assertTrue(reply.endsWith("Couldn't create 2 more; check my permissions and the logs."), reply);
        assertTrue(logged.getFirst().getDescription().contains("created 2 invite(s)"));
    }

    @Test
    void withoutPermissionNothingIsLogged() {
        canCreate = false;

        commands.create(slash(), 2, channel("general")).join();

        assertEquals("I do not have the correct permission to create invites in <#general>!", DiscordFakes.lastEditText(recorder));
        assertTrue(logged.isEmpty());
    }

    @Test
    void deletingRemovesEveryInvite() {
        existing = List.of(invite("a"), invite("b"));

        commands.delete(slash()).join();

        assertEquals("Deleted 2 invites!", DiscordFakes.lastEditText(recorder));
        assertTrue(logged.getFirst().getDescription().contains("deleted 2 invite(s)"));
    }

    @Test
    void invitesThatWontDeleteAreCounted() {
        existing = List.of(invite("a"), invite("b"));
        deleteFailures = 1;

        commands.delete(slash()).join();

        assertEquals("Deleted 1 invites! Couldn't delete 1; check the logs.", DiscordFakes.lastEditText(recorder));
    }

    @Test
    void theReplyListsEveryUrl() {
        assertEquals("**Created 2 invites:**\na\nb\n", InviteCommands.createdMessage(List.of("a", "b"), 0));
    }

    private SlashCommandInteraction slash() {
        return slash(List.of(channel("limbo")));
    }

    private SlashCommandInteraction slash(List<TextChannel> limbo) {
        Guild guild = Fakes.of(Guild.class, Map.of(
            "getTextChannelsByName", limbo,
            "retrieveInvites", (CallRecorder.Answer) args -> Fakes.of(RestAction.class, Map.of("submit", (CallRecorder.Answer) submitArgs -> CompletableFuture.completedFuture(existing)))));
        return recorder.fake(SlashCommandInteraction.class, Map.of(
            "getUser", Fakes.of(User.class, Map.of("getId", "1", "getName", "mod", "getAsMention", "<@1>")),
            "getGuild", guild,
            "getHook", recorder.fake(InteractionHook.class, Map.of("isExpired", false))));
    }

    private TextChannel channel(String name) {
        TextChannel[] self = new TextChannel[1];
        Map<String, Object> answers = new HashMap<>();
        answers.put("getName", name);
        answers.put("getId", name);
        answers.put("getAsMention", "<#" + name + ">");
        answers.put("getGuild", Fakes.of(Guild.class, Map.of("getIdLong", 1L)));
        answers.put("createInvite", (CallRecorder.Answer) args -> {
            if (!canCreate) {
                throw new InsufficientPermissionException(self[0], Permission.CREATE_INSTANT_INVITE);
            }
            InviteAction[] action = new InviteAction[1];
            action[0] = Fakes.of(InviteAction.class, Map.of(
                "setUnique", (CallRecorder.Answer) a -> action[0],
                "setMaxAge", (CallRecorder.Answer) a -> action[0],
                "setMaxUses", (CallRecorder.Answer) a -> action[0],
                "submit", (CallRecorder.Answer) a -> {
                    int number = created.incrementAndGet();
                    return failEvery > 0 && number % failEvery == 0
                        ? CompletableFuture.failedFuture(new IllegalStateException("rate limited"))
                        : CompletableFuture.completedFuture(invite("https://discord.gg/" + number));
                }));
            return action[0];
        });
        self[0] = Fakes.of(TextChannel.class, answers);
        return self[0];
    }

    private Invite invite(String url) {
        return Fakes.of(Invite.class, Map.of("getUrl", url, "delete", (CallRecorder.Answer) args ->
            Fakes.of(AuditableRestAction.class, Map.of("submit", (CallRecorder.Answer) submitArgs -> deleteFailures-- > 0
                ? CompletableFuture.failedFuture(new IllegalStateException("gone"))
                : CompletableFuture.completedFuture(null)))));
    }
}
