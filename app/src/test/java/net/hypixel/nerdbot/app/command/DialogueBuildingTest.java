package net.hypixel.nerdbot.app.command;

import net.aerh.imagegenerator.exception.GeneratorException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Building single- and multi-NPC dialogue strings: name prefixing, index interpolation, the Abiphone
 * symbol, {@code {options: ...}} expansion, and the malformed-input errors.
 */
class DialogueBuildingTest {

    @Test
    void singleDialoguePrefixesEachLineWithNpcName() {
        assertEquals(
            "&e[NPC] Steve&f: Hello\n&e[NPC] Steve&f: Bye",
            GeneratorCommands.buildSingleDialogue("Steve", "Hello\\nBye", false)
        );
    }

    @Test
    void singleDialogueIncludesAbiphoneSymbolWhenEnabled() {
        assertEquals(
            "&e[NPC] Steve&f: &b%%ABIPHONE%%&f Hello",
            GeneratorCommands.buildSingleDialogue("Steve", "Hello", true)
        );
    }

    @Test
    void singleDialogueExpandsOptionsBlock() {
        assertEquals(
            "&e[NPC] Steve&f: Hi \n&eSelect an option: &f&aYes&f &aNo&f ",
            GeneratorCommands.buildSingleDialogue("Steve", "Hi {options: Yes, No}", false)
        );
    }

    @Test
    void singleDialogueRejectsOptionsBlockWithNothingAfterMarker() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.buildSingleDialogue("Steve", "Hello {options:", false));

        assertEquals("Malformed {options: ...} block in dialogue (line 1)! Expected format: {options: Option 1, Option 2}", exception.getMessage());
    }

    @Test
    void singleDialogueRejectsEmptyOptionsBlock() {
        assertThrows(GeneratorException.class, () -> GeneratorCommands.buildSingleDialogue("Steve", "Hello {options:}", false));
    }

    @Test
    void singleDialogueReportsLineNumberOfMalformedOptionsBlock() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.buildSingleDialogue("Steve", "Hello\\nBye {options:", false));

        assertTrue(exception.getMessage().contains("line 2"));
    }

    @Test
    void multiDialogueInterpolatesNpcNamesByIndex() {
        assertEquals(
            "&e[NPC] Alice&f: Hi\n&e[NPC] Bob&f: Hey",
            GeneratorCommands.buildMultiDialogue("Alice, Bob", "0, Hi\\n1, Hey", false)
        );
    }

    @Test
    void multiDialogueIncludesAbiphoneSymbolWhenEnabled() {
        assertEquals(
            "&e[NPC] Alice&f: &b%%ABIPHONE%%&f Hi",
            GeneratorCommands.buildMultiDialogue("Alice", "0, Hi", true)
        );
    }

    @Test
    void multiDialogueClampsTooLargeIndexToLastName() {
        assertEquals(
            "&e[NPC] Bob&f: Hi",
            GeneratorCommands.buildMultiDialogue("Alice, Bob", "5, Hi", false)
        );
    }

    @Test
    void multiDialogueExpandsOptionsBlock() {
        assertEquals(
            "&e[NPC] Alice&f: Hi \n&eSelect an option: &f&aYes&f &aNo&f ",
            GeneratorCommands.buildMultiDialogue("Alice, Bob", "0, Hi {options: Yes, No}", false)
        );
    }

    @Test
    void multiDialogueRejectsLineWithoutComma() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.buildMultiDialogue("Alice, Bob", "0", false));

        assertEquals("Each line must start with an NPC index followed by a comma (line 1)! Example: 0, Hello!", exception.getMessage());
    }

    @Test
    void multiDialogueRejectsNonNumericIndex() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.buildMultiDialogue("Alice, Bob", "abc, Hi", false));

        assertEquals("Invalid NPC name index found in dialogue: abc (line 1)", exception.getMessage());
    }

    @Test
    void multiDialogueRejectsNegativeIndex() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.buildMultiDialogue("Alice, Bob", "-1, Hi", false));

        assertEquals("Invalid NPC name index found in dialogue: -1 (line 1)", exception.getMessage());
    }

    @Test
    void multiDialogueRejectsMalformedOptionsBlock() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.buildMultiDialogue("Alice, Bob", "0, Hi\\n1, Hey {options:", false));

        assertTrue(exception.getMessage().contains("line 2"));
    }
}
