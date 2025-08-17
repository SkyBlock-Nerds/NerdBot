package net.hypixel.nerdbot.generator.item;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.awt.image.BufferedImage;

@Getter
@Setter
@ToString
public class InventoryItem {

    private int[] slot;
    private int[] amount;
    private String itemName;
    private String extraContent;
    private BufferedImage itemImage;
    private Integer durabilityPercent;

    public InventoryItem(int slot, int amount, String itemName, String extraContent, Integer durabilityPercent) {
        this(new int[]{slot}, new int[]{amount}, itemName, extraContent, durabilityPercent);
    }

    public InventoryItem(int[] slots, int[] amounts, String itemName, String extraContent, Integer durabilityPercent) {
        this.slot = slots;
        this.amount = amounts;
        this.itemName = itemName != null ? itemName.toLowerCase() : null;
        this.extraContent = extraContent != null ? extraContent.toLowerCase() : null;
        this.durabilityPercent = durabilityPercent;
    }
}
