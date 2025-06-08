package net.hypixel.nerdbot.service.orangejuice.requestmodels.generator;

import lombok.Data;

@Data
public class SingleDialogueGeneratorRequest {

    private String npcName;

    private String[] dialogue;

    private Integer maxLineLength;

    private Boolean abiphone;

    private String skinValue;
}