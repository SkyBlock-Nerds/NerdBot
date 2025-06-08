package net.hypixel.nerdbot.service.orangejuice.requestmodels.generator;

import lombok.Data;
import net.hypixel.nerdbot.service.orangejuice.requestmodels.generator.submodels.InventoryItem;

@Data
public class RecipeGeneratorRequest {

    private InventoryItem[] recipe;

    private Boolean renderBackground;
}