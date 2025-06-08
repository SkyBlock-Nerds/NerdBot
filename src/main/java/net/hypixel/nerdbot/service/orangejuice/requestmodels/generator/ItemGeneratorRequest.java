package net.hypixel.nerdbot.service.orangejuice.requestmodels.generator;

import lombok.Data;

@Data
public class ItemGeneratorRequest {

    private String itemId;

    private String skinValue;

    private Boolean hoverEffect;

    private Boolean enchanted;

    private String data;
}