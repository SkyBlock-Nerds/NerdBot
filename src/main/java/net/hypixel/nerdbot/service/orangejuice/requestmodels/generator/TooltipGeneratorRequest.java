package net.hypixel.nerdbot.service.orangejuice.requestmodels.generator;

import net.hypixel.nerdbot.service.orangejuice.requestmodels.generator.submodels.InventoryItem;

public class TooltipGeneratorRequest {
    private String itemName;

    private String itemLore;

    private String type;

    private String rarity;

    private String itemId;

    private String skinValue;

    private InventoryItem[] recipe;

    private Integer alpha;

    private Integer padding;

    private Boolean disableRarityLineBreak;

    private Boolean enchanted;

    private Boolean centered;

    private Boolean paddingFirstLine;

    private Integer maxLineLength;

    private String tooltipSide;

    private Boolean renderBorder;
}