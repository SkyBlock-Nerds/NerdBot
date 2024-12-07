package net.hypixel.nerdbot.generator.impl.tooltip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@AllArgsConstructor
@ToString
public class TooltipSettings {

    protected final String lore;
    protected final String name;
    protected final int alpha;
    protected final int padding;
    protected final int maxLineLength;
    protected final boolean renderBorder;

}