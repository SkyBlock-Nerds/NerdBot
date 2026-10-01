package net.hypixel.nerdbot.app.command;

import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.tools.DialogueRequest;
import net.aerh.imagegenerator.tools.DisplayRequest;
import net.aerh.imagegenerator.tools.InventoryRequest;
import net.aerh.imagegenerator.tools.ItemRequest;
import net.aerh.imagegenerator.tools.PowerStoneRequest;
import net.aerh.imagegenerator.tools.RecipeRequest;
import net.aerh.imagegenerator.tools.TextRequest;
import org.jetbrains.annotations.Nullable;

/**
 * Maps raw slash command option values onto the library's request records. Null means the option
 * was omitted and the library default applies. Kept free of JDA types so it is unit testable.
 */
final class GeneratorRequests {

    private GeneratorRequests() {
    }

    static DisplayRequest display(@Nullable String itemId, @Nullable String itemModel, @Nullable String data, @Nullable String color,
                                  @Nullable Boolean enchanted, @Nullable Boolean hoverEffect, @Nullable String skinValue,
                                  @Nullable Integer durability, @Nullable PackId packId) {
        return DisplayRequest.builder()
            .itemId(itemId).itemModel(itemModel).data(data).color(color)
            .enchanted(enchanted).hoverEffect(hoverEffect).skinValue(skinValue).durability(durability).packId(packId)
            .build();
    }

    static ItemRequest item(String itemName, String itemLore, @Nullable String type, @Nullable String rarity,
                            @Nullable String itemId, @Nullable String itemModel, @Nullable String color, @Nullable String skinValue,
                            @Nullable String recipe, @Nullable Integer alpha, @Nullable Integer padding, @Nullable Boolean enchanted,
                            @Nullable Boolean centered, @Nullable Boolean firstLinePadding, @Nullable Integer maxLineLength,
                            @Nullable String tooltipSide, @Nullable Boolean renderBorder, @Nullable Integer durability,
                            @Nullable PackId packId, @Nullable String tooltipStyle) {
        return ItemRequest.builder()
            .itemName(itemName).itemLore(itemLore).type(type).rarity(rarity)
            .itemId(itemId).itemModel(itemModel).color(color).skinValue(skinValue).recipe(recipe)
            .alpha(alpha).padding(padding).enchanted(enchanted).centered(centered).firstLinePadding(firstLinePadding)
            .maxLineLength(maxLineLength).tooltipSide(tooltipSide).renderBorder(renderBorder).durability(durability)
            .packId(packId).tooltipStyle(tooltipStyle)
            .build();
    }

    static TextRequest text(String text, @Nullable Boolean centered, @Nullable Integer alpha, @Nullable Integer padding,
                            @Nullable Integer maxLineLength, @Nullable Boolean renderBorder, @Nullable PackId packId,
                            @Nullable String tooltipStyle) {
        return TextRequest.builder()
            .text(text).centered(centered).alpha(alpha).padding(padding).maxLineLength(maxLineLength)
            .renderBorder(renderBorder).packId(packId).tooltipStyle(tooltipStyle)
            .build();
    }

    static InventoryRequest inventory(int rows, int slotsPerRow, String inventoryString, @Nullable String hoveredItemString,
                                      @Nullable String containerName, @Nullable Boolean drawBorder, @Nullable Integer maxLineLength,
                                      boolean animateGlint, @Nullable PackId packId) {
        return InventoryRequest.builder()
            .rows(rows).slotsPerRow(slotsPerRow).inventoryString(inventoryString).hoveredItemString(hoveredItemString)
            .containerName(containerName).drawBorder(drawBorder).maxLineLength(maxLineLength).animateGlint(animateGlint)
            .packId(packId)
            .build();
    }

    static RecipeRequest recipe(String recipe, @Nullable Boolean renderBackground, @Nullable PackId packId) {
        return RecipeRequest.builder().recipe(recipe).renderBackground(renderBackground).packId(packId).build();
    }

    static PowerStoneRequest powerStone(String powerName, String powerStrength, int magicalPower, @Nullable String scalingStats,
                                        @Nullable String uniqueBonus, @Nullable String itemId, @Nullable String color,
                                        @Nullable String skinValue, @Nullable Integer alpha, @Nullable Integer padding,
                                        @Nullable Boolean selected, @Nullable Boolean enchanted, @Nullable PackId packId) {
        return PowerStoneRequest.builder()
            .powerName(powerName).powerStrength(powerStrength).magicalPower(magicalPower)
            .scalingStats(scalingStats).uniqueBonus(uniqueBonus).itemId(itemId).color(color).skinValue(skinValue)
            .alpha(alpha).padding(padding).selected(selected).enchanted(enchanted).packId(packId)
            .build();
    }

    static DialogueRequest dialogueSingle(String npcName, String dialogue, @Nullable Integer maxLineLength, @Nullable Boolean abiphone,
                                          @Nullable String skinValue, @Nullable Boolean renderBackground, @Nullable PackId packId) {
        return DialogueRequest.single(npcName, dialogue)
            .maxLineLength(maxLineLength).abiphone(abiphone).skinValue(skinValue).renderBackground(renderBackground).packId(packId)
            .build();
    }

    static DialogueRequest dialogueMulti(String npcNames, String dialogue, @Nullable Integer maxLineLength, @Nullable Boolean abiphone,
                                         @Nullable String skinValue, @Nullable Boolean renderBackground, @Nullable PackId packId) {
        return DialogueRequest.multi(npcNames, dialogue)
            .maxLineLength(maxLineLength).abiphone(abiphone).skinValue(skinValue).renderBackground(renderBackground).packId(packId)
            .build();
    }
}
