package net.hypixel.nerdbot.app.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Building the shareable {@code /gen powerstone} command string from the item and enchanted
 * options.
 */
class PowerStoneCommandStringTest {

    @Test
    void appendsItemIdWhenPresent() {
        assertEquals("/gen powerstone item_id: stick",
            GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", "stick", false));
    }

    @Test
    void omitsItemIdWhenNullOrBlank() {
        assertEquals("/gen powerstone", GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", null, false));
        assertEquals("/gen powerstone", GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", "  ", false));
    }

    @Test
    void appendsEnchantedWhenTrue() {
        assertEquals("/gen powerstone item_id: stick enchanted: True",
            GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", "stick", true));
    }
}
