package net.hypixel.nerdbot.service.orangejuice.requestmodels.generator;

import lombok.Data;

@Data
public class TextGeneratorRequest {

    private String text;

    private Boolean centered;

    private Integer alpha;

    private Integer padding;

    private Integer maxLineLength;

    private Boolean renderBorder;
}