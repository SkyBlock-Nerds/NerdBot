package net.hypixel.nerdbot.app.command;

import net.aerh.imagegenerator.exception.GeneratorException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parsing a comma-separated {@code stat:value} string into a name-to-value map: multiple entries,
 * whitespace trimming, duplicate summing, blank handling, and the malformed-input errors.
 */
class PowerStoneStatParsingTest {

    @Test
    void parsesMultipleEntries() {
        assertEquals(Map.of("health", -50, "damage", 10),
            GeneratorCommands.parseStatsToMap("health:-50,damage:10"));
    }

    @Test
    void trimsWhitespace() {
        assertEquals(Map.of("health", -50), GeneratorCommands.parseStatsToMap("  health : -50  "));
    }

    @Test
    void returnsEmptyForNullOrBlank() {
        assertTrue(GeneratorCommands.parseStatsToMap(null).isEmpty());
        assertTrue(GeneratorCommands.parseStatsToMap("   ").isEmpty());
    }

    @Test
    void sumsDuplicateStats() {
        assertEquals(Map.of("strength", 15), GeneratorCommands.parseStatsToMap("strength:5,strength:10"));
    }

    @Test
    void ignoresBlankEntries() {
        assertEquals(Map.of("health", 10, "damage", 5), GeneratorCommands.parseStatsToMap("health:10,,damage:5,"));
    }

    @Test
    void rejectsInvalidFormat() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.parseStatsToMap("health"));

        assertTrue(exception.getMessage().contains("invalid format"));
    }

    @Test
    void rejectsNonNumericValue() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.parseStatsToMap("health:abc"));

        assertTrue(exception.getMessage().contains("Invalid number"));
    }
}
