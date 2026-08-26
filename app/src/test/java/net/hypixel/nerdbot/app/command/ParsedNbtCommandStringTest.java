package net.hypixel.nerdbot.app.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Building the shareable {@code /gen item} command string from a parsed NBT payload: an item
 * carrying {@code minecraft:item_model} round-trips as {@code item_model}, everything else as
 * {@code item_id}.
 */
class ParsedNbtCommandStringTest {

    private static final String BASE = "/gen item item_name: &fSafari Belt";

    @Test
    void appendsItemIdWhenTheItemHasNoModel() {
        assertEquals(BASE + " item_id: paper",
            GeneratorCommands.appendParsedItemOptions(BASE, "paper", null, null, false));
    }

    @Test
    void appendsItemModelInsteadOfItemIdWhenPresent() {
        assertEquals(BASE + " item_model: hypixel_skyblock:item/island_relevant/safari/safari_belt",
            GeneratorCommands.appendParsedItemOptions(BASE, "paper",
                "hypixel_skyblock:item/island_relevant/safari/safari_belt", null, false));
    }

    @Test
    void stripsTheMinecraftNamespaceFromTheItemIdOnly() {
        assertEquals(BASE + " item_id: paper",
            GeneratorCommands.appendParsedItemOptions(BASE, "minecraft:paper", null, null, false));
        assertEquals(BASE + " item_model: minecraft:item/diamond_sword",
            GeneratorCommands.appendParsedItemOptions(BASE, "minecraft:paper", "minecraft:item/diamond_sword", null, false));
    }

    @Test
    void omitsBothWhenNeitherIsPresent() {
        assertEquals(BASE, GeneratorCommands.appendParsedItemOptions(BASE, null, null, null, false));
        assertEquals(BASE, GeneratorCommands.appendParsedItemOptions(BASE, "  ", "  ", null, false));
    }

    @Test
    void appendsSkinValueAlongsideAnItemModel() {
        assertEquals(BASE + " item_model: hypixel_skyblock:item/belt skin_value: abc123",
            GeneratorCommands.appendParsedItemOptions(BASE, "player_head", "hypixel_skyblock:item/belt", "abc123", false));
    }

    @Test
    void appendsEnchantedLast() {
        assertEquals(BASE + " item_model: hypixel_skyblock:item/belt enchanted: True",
            GeneratorCommands.appendParsedItemOptions(BASE, "paper", "hypixel_skyblock:item/belt", null, true));
    }
}
