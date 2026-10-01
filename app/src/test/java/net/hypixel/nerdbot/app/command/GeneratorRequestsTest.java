package net.hypixel.nerdbot.app.command;

import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.tools.DialogueRequest;
import net.aerh.imagegenerator.tools.DisplayRequest;
import net.aerh.imagegenerator.tools.InventoryRequest;
import net.aerh.imagegenerator.tools.ItemRequest;
import net.aerh.imagegenerator.tools.PowerStoneRequest;
import net.aerh.imagegenerator.tools.RecipeRequest;
import net.aerh.imagegenerator.tools.TextRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every handler maps its raw options through one of these functions; omitted options must become the library defaults. */
class GeneratorRequestsTest {

    private static final PackId PACK = PackId.parse("hypixel:skyblock");

    @Test
    void displayPassesValuesAndDefaults() {
        DisplayRequest full = GeneratorRequests.display("stone", null, "enchant", "red", true, true, "skin", 50, PACK);
        DisplayRequest omitted = GeneratorRequests.display(null, "x:y", null, null, null, null, null, null, null);

        assertEquals(DisplayRequest.builder().itemId("stone").data("enchant").color("red").enchanted(true).hoverEffect(true)
            .skinValue("skin").durability(50).packId(PACK).build(), full);
        assertEquals(DisplayRequest.builder().itemModel("x:y").build(), omitted);
    }

    @Test
    void itemPassesValuesAndDefaults() {
        ItemRequest omitted = GeneratorRequests.item("Name", "Lore", null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null);
        ItemRequest full = GeneratorRequests.item("Name", "Lore", "SWORD", "epic", "stone", null, "red", "skin", "stone:1",
            10, 2, true, true, false, 50, "left", false, 20, PACK, "x:y");

        assertEquals(ItemRequest.builder().itemName("Name").itemLore("Lore").build(), omitted);
        assertEquals(ItemRequest.builder().itemName("Name").itemLore("Lore").type("SWORD").rarity("epic").itemId("stone")
            .color("red").skinValue("skin").recipe("stone:1").alpha(10).padding(2).enchanted(true).centered(true)
            .firstLinePadding(false).maxLineLength(50).tooltipSide(MinecraftTooltipGenerator.TooltipSide.LEFT)
            .renderBorder(false).durability(20).packId(PACK).tooltipStyle("x:y").build(), full);
    }

    @Test
    void textPassesValuesAndDefaults() {
        assertEquals(TextRequest.builder().text("hi").build(),
            GeneratorRequests.text("hi", null, null, null, null, null, null, null));
        assertEquals(TextRequest.builder().text("hi").centered(true).alpha(5).padding(1).maxLineLength(40).renderBorder(true).packId(PACK).tooltipStyle("x:y").build(),
            GeneratorRequests.text("hi", true, 5, 1, 40, true, PACK, "x:y"));
    }

    @Test
    void inventoryPassesValuesAndDefaults() {
        assertEquals(InventoryRequest.builder().rows(2).slotsPerRow(9).inventoryString("stone:1").build(),
            GeneratorRequests.inventory(2, 9, "stone:1", null, null, null, null, false, null));
        assertEquals(InventoryRequest.builder().rows(2).slotsPerRow(9).inventoryString("stone:1").hoveredItemString("h")
            .containerName("c").drawBorder(false).maxLineLength(20).animateGlint(true).packId(PACK).build(),
            GeneratorRequests.inventory(2, 9, "stone:1", "h", "c", false, 20, true, PACK));
    }

    @Test
    void recipePassesValuesAndDefaults() {
        assertEquals(RecipeRequest.builder().recipe("stone:1").build(), GeneratorRequests.recipe("stone:1", null, null));
        assertEquals(RecipeRequest.builder().recipe("stone:1").renderBackground(false).packId(PACK).build(),
            GeneratorRequests.recipe("stone:1", false, PACK));
    }

    @Test
    void powerStonePassesValuesAndDefaults() {
        assertEquals(PowerStoneRequest.builder().powerName("n").powerStrength("s").magicalPower(5).build(),
            GeneratorRequests.powerStone("n", "s", 5, null, null, null, null, null, null, null, null, null, null));
        assertEquals(PowerStoneRequest.builder().powerName("n").powerStrength("s").magicalPower(5).scalingStats("a:1")
            .uniqueBonus("b:2").itemId("stone").color("red").skinValue("skin").alpha(1).padding(2).selected(false)
            .enchanted(true).packId(PACK).build(),
            GeneratorRequests.powerStone("n", "s", 5, "a:1", "b:2", "stone", "red", "skin", 1, 2, false, true, PACK));
    }

    @Test
    void dialoguePassesValuesAndDefaults() {
        assertEquals(DialogueRequest.single("Steve", "Hi").build(),
            GeneratorRequests.dialogueSingle("Steve", "Hi", null, null, null, null, null));
        assertEquals(DialogueRequest.multi("Steve, Alex", "0, Hi").maxLineLength(50).abiphone(true).skinValue("skin")
            .renderBackground(true).packId(PACK).build(),
            GeneratorRequests.dialogueMulti("Steve, Alex", "0, Hi", 50, true, "skin", true, PACK));
    }
}
