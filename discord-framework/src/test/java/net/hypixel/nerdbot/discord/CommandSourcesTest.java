package net.hypixel.nerdbot.discord;

import net.aerh.slashcommands.SlashCommandManager;
import net.aerh.slashcommands.SlashCommandManagerBuilder;
import net.dv8tion.jda.api.JDA;
import net.hypixel.nerdbot.discord.commandfixture.PingCommands;
import net.hypixel.nerdbot.discord.testsupport.Fakes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandSourcesTest {

    private static final String FIXTURE_PACKAGE = "net.hypixel.nerdbot.discord.commandfixture";

    private final SlashCommandManagerBuilder builder = SlashCommandManager.builder()
        .withJDA(Fakes.of(JDA.class, Map.of()));

    @Test
    void registersTheGivenInstances() {
        PingCommands commands = new PingCommands();

        assertTrue(AbstractDiscordBot.addCommandSources(builder, List.of(commands), null));

        SlashCommandManager manager = builder.build();
        assertSame(commands, manager.getRegistry().getCommand("ping").getInstance());
    }

    @Test
    void scansThePackageWhenNoInstancesAreGiven() {
        assertTrue(AbstractDiscordBot.addCommandSources(builder, List.of(), FIXTURE_PACKAGE));

        SlashCommandManager manager = builder.build();
        assertTrue(manager.getRegistry().getAllCommands().containsKey("ping"));
    }

    @Test
    void hasNothingToRegisterWithoutInstancesOrPackage() {
        assertFalse(AbstractDiscordBot.addCommandSources(builder, List.of(), null));
        assertFalse(AbstractDiscordBot.addCommandSources(builder, List.of(), "  "));
    }

    @Test
    void refusesInstancesAndPackageTogether() {
        assertThrows(IllegalStateException.class,
            () -> AbstractDiscordBot.addCommandSources(builder, List.of(new PingCommands()), FIXTURE_PACKAGE));
    }

    @Test
    void blankPackageDoesNotConflictWithInstances() {
        assertTrue(AbstractDiscordBot.addCommandSources(builder, List.of(new PingCommands()), ""));
    }
}
