package net.hypixel.nerdbot.discord.testsupport;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallRecorderTest {

    private final CallRecorder recorder = new CallRecorder();

    @Test
    void builderChainsKeepRecording() {
        IReplyCallback interaction = recorder.fake(SlashCommandInteraction.class);

        interaction.deferReply().setEphemeral(true).queue();

        assertEquals(true, recorder.last("setEphemeral").arg(0));
        assertTrue(recorder.called("queue"));
    }

    @Test
    void defaultMethodsRunTheirRealImplementation() {
        IReplyCallback interaction = recorder.fake(SlashCommandInteraction.class);

        interaction.reply("hello").queue();

        assertEquals("hello", recorder.last("reply").arg(0));
        assertTrue(recorder.called("deferReply"), "JDA's reply(String) goes through deferReply()");
    }

    @Test
    void nestedFakesAreReusedForTheSameMethod() {
        SlashCommandInteraction interaction = recorder.fake(SlashCommandInteraction.class);

        InteractionHook first = interaction.getHook();

        assertSame(first, interaction.getHook());
    }

    @Test
    void answersWinOverEverythingElse() {
        IReplyCallback interaction = recorder.fake(SlashCommandInteraction.class, Map.of("getToken", "abc"));

        assertEquals("abc", interaction.getToken());
    }

    @Test
    void answerFunctionsSeeTheArguments() {
        List<Object> seen = new ArrayList<>();
        InteractionHook hook = recorder.fake(InteractionHook.class, Map.of("setEphemeral", (CallRecorder.Answer) args -> {
            seen.add(args[0]);
            return null;
        }));

        hook.setEphemeral(true);

        assertEquals(List.of(true), seen);
    }

    @Test
    void varargsAreFlattenedIntoTheArguments() {
        InteractionHook hook = recorder.fake(InteractionHook.class);
        MessageEmbed first = new EmbedBuilder().setTitle("one").build();
        MessageEmbed second = new EmbedBuilder().setTitle("two").build();

        hook.editOriginalEmbeds(first, second).queue();

        assertEquals(List.of(first, second), recorder.last("editOriginalEmbeds").args());
    }

    @Test
    void objectMethodsAreNotRecorded() {
        InteractionHook hook = recorder.fake(InteractionHook.class);

        assertTrue(hook.equals(hook));
        assertFalse(hook.toString().isEmpty());

        assertTrue(recorder.calls().isEmpty());
    }

    @Test
    void lastNamesTheCallsThatDidHappen() {
        recorder.fake(InteractionHook.class).getExpirationTimestamp();

        AssertionError thrown = assertThrows(AssertionError.class, () -> recorder.last("editOriginal"));

        assertTrue(thrown.getMessage().contains("getExpirationTimestamp"));
    }
}
