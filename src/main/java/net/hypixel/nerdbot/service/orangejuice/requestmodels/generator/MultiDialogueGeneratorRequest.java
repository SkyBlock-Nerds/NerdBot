package net.hypixel.nerdbot.service.orangejuice.requestmodels.generator;

import lombok.Data;
import net.hypixel.nerdbot.service.orangejuice.requestmodels.generator.submodels.MultiDialogueLine;

@Data
public class MultiDialogueGeneratorRequest {

    private String[] npcNames;

    private MultiDialogueLine[] dialogue;

    private Integer maxLineLength;

    private Boolean abiphone;

    private String skinValue;
}