package net.hypixel.nerdbot.app.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Building the shareable {@code /gen powerstone} command string from the item, enchanted, and
 * animated options.
 */
class PowerStoneCommandStringTest {

    @Test
    void appendsItemIdWhenPresent() {
        assertEquals("/gen powerstone item_id: stick",
            GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", "stick", false, true));
    }

    @Test
    void omitsItemIdWhenNullOrBlank() {
        assertEquals("/gen powerstone", GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", null, false, true));
        assertEquals("/gen powerstone", GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", "  ", false, true));
    }

    @Test
    void appendsEnchantedWhenTrue() {
        assertEquals("/gen powerstone item_id: stick enchanted: True",
            GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", "stick", true, true));
    }

    @Test
    void omitsAnimatedWhenDefaultedOn() {
        assertEquals("/gen powerstone item_id: stick",
            GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", "stick", false, true));
    }

    @Test
    void appendsAnimatedFalseWhenOptedOut() {
        assertEquals("/gen powerstone item_id: stick animated: False",
            GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", "stick", false, false));
    }

    @Test
    void appendsAllOptionsInOrder() {
        assertEquals("/gen powerstone item_id: stick enchanted: True animated: False",
            GeneratorCommands.appendPowerStoneItemOptions("/gen powerstone", "stick", true, false));
    }
}
