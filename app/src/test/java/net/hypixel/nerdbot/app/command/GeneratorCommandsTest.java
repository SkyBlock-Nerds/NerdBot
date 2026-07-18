package net.hypixel.nerdbot.app.command;

import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.dv8tion.jda.api.utils.FileUpload;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.DiscordUser;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.generator.GeneratorHistory;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorCommandsTest {

    @Test
    void renderAttachmentUploadsStaticRenderAsPng() throws IOException {
        GeneratedObject staticObject = new GeneratedObject(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));

        try (FileUpload upload = GeneratorCommands.renderAttachment(staticObject, "item")) {
            assertEquals("item.png", upload.getName());
        }
    }

    @Test
    void renderAttachmentUploadsAnimatedRenderAsGif() throws IOException {
        byte[] gifData = {'G', 'I', 'F', '8', '9', 'a'};
        GeneratedObject animatedObject = new GeneratedObject(
            gifData,
            List.of(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)),
            50
        );

        try (FileUpload upload = GeneratorCommands.renderAttachment(animatedObject, "item")) {
            assertEquals("item.gif", upload.getName());
            assertArrayEquals(gifData, upload.getData().readAllBytes());
        }
    }

    @Test
    void renderAttachmentUsesGivenBaseNameForFileExtension() throws IOException {
        GeneratedObject staticObject = new GeneratedObject(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        GeneratedObject animatedObject = new GeneratedObject(
            new byte[]{1, 2, 3, 4},
            List.of(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)),
            50
        );

        try (FileUpload pngUpload = GeneratorCommands.renderAttachment(staticObject, "recipe");
             FileUpload gifUpload = GeneratorCommands.renderAttachment(animatedObject, "powerstone")) {
            assertEquals("recipe.png", pngUpload.getName());
            assertEquals("powerstone.gif", gifUpload.getName());
        }
    }

    @Test
    void appendPowerStoneItemOptionsAppendsItemIdWhenPresent() {
        assertEquals("/gen powerstone item_id: stick",
            GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", "stick", false, true));
    }

    @Test
    void appendPowerStoneItemOptionsOmitsItemIdWhenNullOrBlank() {
        assertEquals("/gen powerstone", GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", null, false, true));
        assertEquals("/gen powerstone", GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", "  ", false, true));
    }

    @Test
    void appendPowerStoneItemOptionsAppendsEnchantedWhenTrue() {
        assertEquals("/gen powerstone item_id: stick enchanted: True",
            GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", "stick", true, true));
    }

    @Test
    void appendPowerStoneItemOptionsOmitsAnimatedWhenDefaultedOn() {
        assertEquals("/gen powerstone item_id: stick",
            GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", "stick", false, true));
    }

    @Test
    void appendPowerStoneItemOptionsAppendsAnimatedFalseWhenOptedOut() {
        assertEquals("/gen powerstone item_id: stick animated: False",
            GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", "stick", false, false));
    }

    @Test
    void appendPowerStoneItemOptionsAppendsAllOptionsInOrder() {
        assertEquals("/gen powerstone item_id: stick enchanted: True animated: False",
            GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", "stick", true, false));
    }

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

    @Test
    void buildSingleDialoguePrefixesEachLineWithNpcName() {
        assertEquals(
            "&e[NPC] Steve&f: Hello\n&e[NPC] Steve&f: Bye",
            GeneratorCommands.buildSingleDialogue("Steve", "Hello\\nBye", false)
        );
    }

    @Test
    void buildSingleDialogueIncludesAbiphoneSymbolWhenEnabled() {
        assertEquals(
            "&e[NPC] Steve&f: &b%%ABIPHONE%%&f Hello",
            GeneratorCommands.buildSingleDialogue("Steve", "Hello", true)
        );
    }

    @Test
    void buildSingleDialogueExpandsOptionsBlock() {
        assertEquals(
            "&e[NPC] Steve&f: Hi \n&eSelect an option: &f&aYes&f &aNo&f ",
            GeneratorCommands.buildSingleDialogue("Steve", "Hi {options: Yes, No}", false)
        );
    }

    @Test
    void buildSingleDialogueRejectsOptionsBlockWithNothingAfterMarker() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.buildSingleDialogue("Steve", "Hello {options:", false));

        assertEquals("Malformed {options: ...} block in dialogue (line 1)! Expected format: {options: Option 1, Option 2}", exception.getMessage());
    }

    @Test
    void buildSingleDialogueRejectsEmptyOptionsBlock() {
        assertThrows(GeneratorException.class, () -> GeneratorCommands.buildSingleDialogue("Steve", "Hello {options:}", false));
    }

    @Test
    void buildSingleDialogueReportsLineNumberOfMalformedOptionsBlock() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.buildSingleDialogue("Steve", "Hello\\nBye {options:", false));

        assertTrue(exception.getMessage().contains("line 2"));
    }

    @Test
    void buildMultiDialogueInterpolatesNpcNamesByIndex() {
        assertEquals(
            "&e[NPC] Alice&f: Hi\n&e[NPC] Bob&f: Hey",
            GeneratorCommands.buildMultiDialogue("Alice, Bob", "0, Hi\\n1, Hey", false)
        );
    }

    @Test
    void buildMultiDialogueIncludesAbiphoneSymbolWhenEnabled() {
        assertEquals(
            "&e[NPC] Alice&f: &b%%ABIPHONE%%&f Hi",
            GeneratorCommands.buildMultiDialogue("Alice", "0, Hi", true)
        );
    }

    @Test
    void buildMultiDialogueClampsTooLargeIndexToLastName() {
        assertEquals(
            "&e[NPC] Bob&f: Hi",
            GeneratorCommands.buildMultiDialogue("Alice, Bob", "5, Hi", false)
        );
    }

    @Test
    void buildMultiDialogueExpandsOptionsBlock() {
        assertEquals(
            "&e[NPC] Alice&f: Hi \n&eSelect an option: &f&aYes&f &aNo&f ",
            GeneratorCommands.buildMultiDialogue("Alice, Bob", "0, Hi {options: Yes, No}", false)
        );
    }

    @Test
    void buildMultiDialogueRejectsLineWithoutComma() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.buildMultiDialogue("Alice, Bob", "0", false));

        assertEquals("Each line must start with an NPC index followed by a comma (line 1)! Example: 0, Hello!", exception.getMessage());
    }

    @Test
    void buildMultiDialogueRejectsNonNumericIndex() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.buildMultiDialogue("Alice, Bob", "abc, Hi", false));

        assertEquals("Invalid NPC name index found in dialogue: abc (line 1)", exception.getMessage());
    }

    @Test
    void buildMultiDialogueRejectsNegativeIndex() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.buildMultiDialogue("Alice, Bob", "-1, Hi", false));

        assertEquals("Invalid NPC name index found in dialogue: -1 (line 1)", exception.getMessage());
    }

    @Test
    void buildMultiDialogueRejectsMalformedOptionsBlock() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.buildMultiDialogue("Alice, Bob", "0, Hi\\n1, Hey {options:", false));

        assertTrue(exception.getMessage().contains("line 2"));
    }

    @Test
    void parseStatsToMapParsesMultipleEntries() {
        assertEquals(Map.of("health", -50, "damage", 10),
            GeneratorCommands.parseStatsToMap("health:-50,damage:10"));
    }

    @Test
    void parseStatsToMapTrimsWhitespace() {
        assertEquals(Map.of("health", -50), GeneratorCommands.parseStatsToMap("  health : -50  "));
    }

    @Test
    void parseStatsToMapReturnsEmptyForNullOrBlank() {
        assertTrue(GeneratorCommands.parseStatsToMap(null).isEmpty());
        assertTrue(GeneratorCommands.parseStatsToMap("   ").isEmpty());
    }

    @Test
    void parseStatsToMapSumsDuplicateStats() {
        assertEquals(Map.of("strength", 15), GeneratorCommands.parseStatsToMap("strength:5,strength:10"));
    }

    @Test
    void parseStatsToMapIgnoresBlankEntries() {
        assertEquals(Map.of("health", 10, "damage", 5), GeneratorCommands.parseStatsToMap("health:10,,damage:5,"));
    }

    @Test
    void parseStatsToMapRejectsInvalidFormat() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.parseStatsToMap("health"));

        assertTrue(exception.getMessage().contains("invalid format"));
    }

    @Test
    void parseStatsToMapRejectsNonNumericValue() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.parseStatsToMap("health:abc"));

        assertTrue(exception.getMessage().contains("Invalid number"));
    }

    @Test
    void appendSearchResultsIgnoresEmptyResults() {
        StringBuilder message = new StringBuilder("existing");

        GeneratorCommands.appendSearchResults(message, "Header", List.of());

        assertEquals("existing", message.toString());
    }

    @Test
    void appendSearchResultsAppendsHeaderWithTotalAndEachResult() {
        StringBuilder message = new StringBuilder();

        GeneratorCommands.appendSearchResults(message, "Top", List.of("a", "b"));

        assertEquals("Top (2 total):\n - `a`\n - `b`\n", message.toString());
    }

    @Test
    void appendSearchResultsCapsRenderedLinesButReportsFullTotal() {
        List<String> results = java.util.stream.IntStream.range(0, 15).mapToObj(i -> "item" + i).toList();
        StringBuilder message = new StringBuilder();

        GeneratorCommands.appendSearchResults(message, "Results", results);

        String output = message.toString();
        assertTrue(output.startsWith("Results (15 total):\n"), "header should report the full total");
        assertEquals(10, output.lines().filter(line -> line.startsWith(" - ")).count(), "only 10 results should render");
    }

}
