package net.hypixel.nerdbot.generator.impl.tooltip.skyblock;

import lombok.Getter;
import lombok.ToString;
import net.hypixel.nerdbot.generator.data.Rarity;
import net.hypixel.nerdbot.generator.impl.tooltip.TooltipSettings;

@Getter
@ToString
public class SkyblockItemSettings extends TooltipSettings {

    private final Rarity rarity;
    private final boolean emptyLine;
    private final String type;
    private final boolean normalItem;

    public SkyblockItemSettings (String lore, String name, int alpha, int padding, int maxLineLength, boolean renderBorder, Rarity rarity, boolean emptyLine, String type, boolean normalItem) {
        super(lore, name, alpha, padding, maxLineLength, renderBorder);

        this.rarity = rarity;
        this.emptyLine = emptyLine;
        this.type = type;
        this.normalItem = normalItem;
    }
}