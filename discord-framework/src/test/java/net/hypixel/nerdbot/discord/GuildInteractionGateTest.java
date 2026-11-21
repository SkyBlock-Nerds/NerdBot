package net.hypixel.nerdbot.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuildInteractionGateTest {

    private static final String MAIN_GUILD = "1";
    private static final String OTHER_GUILD = "2";

    private final JDA jda = Fakes.of(JDA.class, Map.of());
    private final CallRecorder recorder = new CallRecorder();
    private final List<GenericEvent> passedOn = new ArrayList<>();
    private final GuildInteractionGate gate = new GuildInteractionGate(passedOn::add, guild -> guild.getId().equals(MAIN_GUILD));

    @Test
    void anInteractionFromTheServedGuildIsPassedOn() {
        GenericInteractionCreateEvent event = event(SlashCommandInteraction.class, MAIN_GUILD);

        gate.onEvent(event);

        assertEquals(List.of(event), passedOn);
        assertFalse(recorder.called("reply"));
    }

    @Test
    void aCommandFromAnotherGuildIsRefusedPrivately() {
        gate.onEvent(event(SlashCommandInteraction.class, OTHER_GUILD));

        assertEquals(List.of(), passedOn);
        assertEquals(GuildInteractionGate.REFUSAL, recorder.last("reply").arg(0));
        assertEquals(true, recorder.last("setEphemeral").arg(0));
        assertTrue(recorder.called("queue"));
    }

    @Test
    void aButtonFromAnotherGuildIsRefusedToo() {
        gate.onEvent(event(ButtonInteraction.class, OTHER_GUILD));

        assertEquals(List.of(), passedOn);
        assertEquals(GuildInteractionGate.REFUSAL, recorder.last("reply").arg(0));
    }

    @Test
    void autocompleteFromAnotherGuildGetsNoChoices() {
        gate.onEvent(event(CommandAutoCompleteInteraction.class, OTHER_GUILD));

        assertEquals(List.of(), passedOn);
        assertEquals(List.of(), recorder.last("replyChoices").arg(0));
    }

    @Test
    void anInteractionOutsideAGuildIsPassedOn() {
        GenericInteractionCreateEvent event = event(SlashCommandInteraction.class, null);

        gate.onEvent(event);

        assertEquals(List.of(event), passedOn);
    }

    @Test
    void otherEventsArePassedOn() {
        ShutdownEvent event = new ShutdownEvent(jda, OffsetDateTime.now(), 1000);

        gate.onEvent(event);

        assertEquals(List.of(event), passedOn);
    }

    private GenericInteractionCreateEvent event(Class<? extends Interaction> type, String guildId) {
        Map<String, Object> answers = new HashMap<>();
        answers.put("getUser", Fakes.of(User.class, Map.of("getId", "7")));
        // A missing answer is null, which is what an interaction outside a guild reports
        if (guildId != null) {
            answers.put("getGuild", Fakes.of(Guild.class, Map.of("getId", guildId)));
        } else {
            answers.put("getGuild", (CallRecorder.Answer) args -> null);
        }
        return new GenericInteractionCreateEvent(jda, 0, recorder.fake(type, answers));
    }
}
