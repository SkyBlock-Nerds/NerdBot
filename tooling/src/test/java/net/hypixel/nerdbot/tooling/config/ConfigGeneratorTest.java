package net.hypixel.nerdbot.tooling.config;

import net.hypixel.nerdbot.discord.config.ExampleValue;
import net.hypixel.nerdbot.discord.config.NoExampleValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigGeneratorTest {

    private final ConfigGenerator generator = new ConfigGenerator();

    static class Defaults {
        int port = 9190;
        double ratio = 0.5;
        boolean enabled = false;
        String name = "SkyBlock";
        List<String> hosts = new ArrayList<>(List.of("resourcepacks.hypixel.net"));
        TimeUnit unit = TimeUnit.HOURS;
    }

    @Test
    void defaultsAreKept() {
        Defaults generated = generator.generate(Defaults.class);

        assertEquals(9190, generated.port);
        assertEquals(0.5, generated.ratio);
        assertEquals(false, generated.enabled);
        assertEquals("SkyBlock", generated.name);
        assertEquals(List.of("resourcepacks.hypixel.net"), generated.hosts);
        assertEquals(TimeUnit.HOURS, generated.unit);
    }

    static class Nested {
        String label;
    }

    static class Empties {
        String channelId;
        String blankRoleId = "";
        String apiUrl;
        String description;
        List<String> ownerIds = List.of();
        Set<String> tags = Set.of();
        Map<String, Integer> limits = Map.of();
        String[] blacklist = {};
        Long periodMs;
        Nested nested;
        List<Nested> children;
    }

    @Test
    void emptyValuesGetExamples() {
        Empties generated = generator.generate(Empties.class);

        assertEquals(ConfigGenerator.EXAMPLE_DISCORD_ID, generated.channelId);
        assertEquals(ConfigGenerator.EXAMPLE_DISCORD_ID, generated.blankRoleId);
        assertEquals(ConfigGenerator.EXAMPLE_URL, generated.apiUrl);
        assertEquals(ConfigGenerator.EXAMPLE_STRING, generated.description);
        assertEquals(List.of(ConfigGenerator.EXAMPLE_DISCORD_ID), generated.ownerIds);
        assertEquals(Set.of(ConfigGenerator.EXAMPLE_STRING), generated.tags);
        assertEquals(Map.of(ConfigGenerator.EXAMPLE_STRING, 1), generated.limits);
        assertArrayEquals(new String[]{ConfigGenerator.EXAMPLE_STRING}, generated.blacklist);
        assertEquals(1L, generated.periodMs);
        assertEquals(ConfigGenerator.EXAMPLE_STRING, generated.nested.label);
        assertEquals(ConfigGenerator.EXAMPLE_STRING, generated.children.getFirst().label);
    }

    @Test
    void onlyNamesEndingInIdOrIdsAreTreatedAsDiscordIds() {
        assertEquals(ConfigGenerator.EXAMPLE_DISCORD_ID, ConfigGenerator.exampleString("guildId"));
        assertEquals(ConfigGenerator.EXAMPLE_DISCORD_ID, ConfigGenerator.exampleString("requiredRoleIds"));
        assertEquals(ConfigGenerator.EXAMPLE_STRING, ConfigGenerator.exampleString("identifier"));
        assertEquals(ConfigGenerator.EXAMPLE_STRING, ConfigGenerator.exampleString("hidden"));
        assertEquals(ConfigGenerator.EXAMPLE_URL, ConfigGenerator.exampleString("downloadUrlTemplate"));
    }

    enum Level {
        READER, WRITER
    }

    static class Annotated {
        @ExampleValue("hypixel:skyblock")
        String packId;
        @ExampleValue("3600000")
        Long periodMs;
        @ExampleValue("0.1")
        Double chance;
        @ExampleValue("true")
        boolean flag;
        @ExampleValue("WRITER")
        Level level;
        @ExampleValue("minecraft:stone")
        List<String> items;
        @ExampleValue("123")
        String[] channels;
        @NoExampleValue
        String variantOf;
        @NoExampleValue
        Map<String, String> styles = Map.of();
    }

    @Test
    void annotationsOverrideOrSuppressTheExample() {
        Annotated generated = generator.generate(Annotated.class);

        assertEquals("hypixel:skyblock", generated.packId);
        assertEquals(3_600_000L, generated.periodMs);
        assertEquals(0.1, generated.chance);
        assertTrue(generated.flag);
        assertEquals(Level.WRITER, generated.level);
        assertEquals(List.of("minecraft:stone"), generated.items);
        assertArrayEquals(new String[]{"123"}, generated.channels);
        assertNull(generated.variantOf);
        assertEquals(Map.of(), generated.styles);
    }

    static class BadEnumExample {
        @ExampleValue("OWNER")
        Level level;
    }

    @Test
    void anExampleThatIsNotAnEnumConstantFails() {
        assertThrows(IllegalArgumentException.class, () -> generator.generate(BadEnumExample.class));
    }

    record ConstructorOnly(String id, String name) {
    }

    static class HoldsConstructorOnly {
        List<ConstructorOnly> badges;
    }

    @Test
    void classesWithoutANoArgConstructorAreBuiltThroughTheirConstructor() {
        ConstructorOnly badge = generator.generate(HoldsConstructorOnly.class).badges.getFirst();

        assertEquals(ConfigGenerator.EXAMPLE_DISCORD_ID, badge.id());
        assertEquals(ConfigGenerator.EXAMPLE_STRING, badge.name());
    }

    static class SelfReferencing {
        SelfReferencing next;
    }

    @Test
    void endlessNestingFailsInsteadOfOverflowing() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> generator.generate(SelfReferencing.class));

        assertTrue(failure.getMessage().contains("deeper than"), failure.getMessage());
    }

    @SuppressWarnings("rawtypes")
    static class RawList {
        List things;
    }

    @Test
    void aCollectionWithoutAnElementTypeFails() {
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> generator.generate(RawList.class));

        assertTrue(failure.getMessage().contains("RawList.things"), failure.getMessage());
    }
}
