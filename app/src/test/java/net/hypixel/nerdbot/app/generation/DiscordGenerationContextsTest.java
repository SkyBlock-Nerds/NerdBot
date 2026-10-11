package net.hypixel.nerdbot.app.generation;

import net.aerh.imagegenerator.context.GenerationContext;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordGenerationContextsTest {

    private static final String FOOLS_CHANNEL = "400";

    private final NerdBotConfig config = new NerdBotConfig();
    private final CallRecorder recorder = new CallRecorder();

    @BeforeEach
    void setUp() {
        config.getChannelConfig().setAprilFoolsGenChannelIds(new String[]{FOOLS_CHANNEL});
        config.getFunConfig().setAprilFoolsTimezone("UTC");
    }

    @Test
    void aprilFirstInAListedChannelIsFools() {
        assertTrue(at("2027-04-01T12:00:00Z").isAprilFoolsIn(FOOLS_CHANNEL));
    }

    @Test
    void anyOtherDayIsNot() {
        assertFalse(at("2027-04-02T12:00:00Z").isAprilFoolsIn(FOOLS_CHANNEL));
        assertFalse(at("2027-03-31T23:59:59Z").isAprilFoolsIn(FOOLS_CHANNEL));
    }

    @Test
    void anUnlistedChannelIsNot() {
        assertFalse(at("2027-04-01T12:00:00Z").isAprilFoolsIn("401"));
    }

    @Test
    void channelIdsMatchIgnoringCase() {
        config.getChannelConfig().setAprilFoolsGenChannelIds(new String[]{"abc"});

        assertTrue(at("2027-04-01T12:00:00Z").isAprilFoolsIn("ABC"));
    }

    @Test
    void theDateIsTakenInTheConfiguredZone() {
        config.getFunConfig().setAprilFoolsTimezone("America/New_York");

        assertFalse(at("2027-04-01T02:00:00Z").isAprilFoolsIn(FOOLS_CHANNEL));
        assertTrue(at("2027-04-01T05:00:00Z").isAprilFoolsIn(FOOLS_CHANNEL));
    }

    @Test
    void aBadZoneDoesNotBreakGeneration() {
        config.getFunConfig().setAprilFoolsTimezone("Not/AZone");

        assertDoesNotThrow(() -> at("2027-04-01T12:00:00Z").isAprilFoolsIn(FOOLS_CHANNEL));
    }

    @Test
    void noListedChannelsMeansNever() {
        config.getChannelConfig().setAprilFoolsGenChannelIds(null);

        assertFalse(at("2027-04-01T12:00:00Z").isAprilFoolsIn(FOOLS_CHANNEL));
    }

    @Test
    void anInteractionWithoutAChannelIsNot() {
        assertFalse(at("2027-04-01T12:00:00Z").isAprilFoolsIn(null));
    }

    @Test
    void theContextCarriesTheChannelAndSendsNoticesAsFollowUps() {
        InteractionHook hook = recorder.fake(InteractionHook.class);
        SlashCommandInteraction interaction = recorder.fake(SlashCommandInteraction.class, Map.of("getChannelId", "500", "getHook", hook));

        GenerationContext context = at("2027-04-01T12:00:00Z").create(interaction, false);
        context.feedback().sendMessage("Using the vanilla texture", false);

        assertEquals("500", context.channelId());
        assertFalse(context.aprilFools());
        assertEquals("Using the vanilla texture", recorder.last("sendMessage").arg(0));
        assertEquals(false, recorder.last("setEphemeral").arg(0));
    }

    @Test
    void noticesAreHiddenWhenTheReplyIsOrWhenTheGeneratorAsks() {
        SlashCommandInteraction interaction = recorder.fake(SlashCommandInteraction.class, Map.of("getChannelId", "500"));
        DiscordGenerationContexts contexts = at("2027-04-01T12:00:00Z");

        contexts.create(interaction, true).feedback().sendMessage("a", false);
        assertEquals(true, recorder.last("setEphemeral").arg(0));

        contexts.create(interaction, false).feedback().sendMessage("b", true);
        assertEquals(true, recorder.last("setEphemeral").arg(0));
    }

    private DiscordGenerationContexts at(String instant) {
        return new DiscordGenerationContexts(() -> config, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
