package net.hypixel.nerdbot.service.orangejuice.requestmodels.generator;

import lombok.Data;
import net.hypixel.nerdbot.service.orangejuice.requestmodels.generator.submodels.InventoryItem;

@Data
public class InventoryGeneratorRequest {

    private InventoryItem[] inventoryItems;

    private Integer rows;

    private Integer columns;

    private String hoveredItemString;

    private String containerName;

    private Boolean renderBorder;
}