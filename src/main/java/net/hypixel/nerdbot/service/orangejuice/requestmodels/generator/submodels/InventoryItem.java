package net.hypixel.nerdbot.service.orangejuice.requestmodels.generator.submodels;

import lombok.Data;

import java.util.List;

@Data
public class InventoryItem {

    private String itemId;

    private List<ItemLocation> locations;

    private Integer amount;

    private String[] extraData;
}