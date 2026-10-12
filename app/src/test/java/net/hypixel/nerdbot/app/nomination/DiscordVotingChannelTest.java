package net.hypixel.nerdbot.app.nomination;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.testsupport.CallRecorder;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiscordVotingChannelTest {

    private final NerdBotConfig config = new NerdBotConfig();
    private final TextChannel channel = Fakes.of(TextChannel.class, Map.of("getId", "42"));
    private final JDA jda = Fakes.of(JDA.class, Map.of("getTextChannelById", (CallRecorder.Answer) args -> "42".equals(args[0]) ? channel : null));
    private final DiscordVotingChannel votingChannel = new DiscordVotingChannel(jda, () -> config);

    @Test
    void theConfiguredChannelIsAvailable() {
        config.getChannelConfig().setMemberVotingChannelId("42");

        assertDoesNotThrow(votingChannel::requireAvailable);
    }

    @Test
    void aChannelThatDoesNotExistIsNotAvailable() {
        config.getChannelConfig().setMemberVotingChannelId("43");

        assertThrows(IllegalStateException.class, votingChannel::requireAvailable);
    }

    @Test
    void aBlankOrMalformedIdIsNotAvailable() {
        config.getChannelConfig().setMemberVotingChannelId("");
        assertThrows(IllegalStateException.class, votingChannel::requireAvailable);

        config.getChannelConfig().setMemberVotingChannelId("voting");
        assertThrows(IllegalStateException.class, votingChannel::requireAvailable);

        config.getChannelConfig().setMemberVotingChannelId(null);
        assertThrows(IllegalStateException.class, votingChannel::requireAvailable);
    }
}
