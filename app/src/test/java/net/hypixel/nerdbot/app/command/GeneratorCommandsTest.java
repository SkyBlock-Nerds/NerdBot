package net.hypixel.nerdbot.app.command;

import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.generator.GeneratorHistory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorCommandsTest {

    @Test
    void getCommandHistoryReturnsEmptyListForNullUser() {
        assertTrue(GeneratorCommands.getCommandHistory(null).isEmpty());
    }

    @Test
    void getCommandHistoryReturnsEmptyListForUserWithoutHistory() {
        DiscordUser discordUser = new DiscordUser();

        assertTrue(GeneratorCommands.getCommandHistory(discordUser).isEmpty());
    }

    @Test
    void getCommandHistoryReturnsEmptyListForUserWithEmptyHistory() {
        DiscordUser discordUser = new DiscordUser();
        discordUser.setGeneratorHistory(new GeneratorHistory());

        assertTrue(GeneratorCommands.getCommandHistory(discordUser).isEmpty());
    }

    @Test
    void getCommandHistoryReturnsStoredCommands() {
        DiscordUser discordUser = new DiscordUser();
        discordUser.setGeneratorHistory(new GeneratorHistory());
        discordUser.getGeneratorHistory().addCommand("/gen item id:stick");
        discordUser.getGeneratorHistory().addCommand("/gen text text:hello");

        assertEquals(List.of("/gen item id:stick", "/gen text text:hello"), GeneratorCommands.getCommandHistory(discordUser));
    }
}
