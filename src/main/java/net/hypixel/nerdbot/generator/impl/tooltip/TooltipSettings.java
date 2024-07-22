package net.hypixel.nerdbot.generator.impl.tooltip;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TooltipSettings {

    private final String name;
    private final boolean emptyLine;
    private final String type;
    private final int alpha;
    private final int padding;
    private final boolean paddingFirstLine;
    private final int maxLineLength;
    private final boolean renderBorder;

}

