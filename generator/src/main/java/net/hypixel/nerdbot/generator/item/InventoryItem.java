package net.hypixel.nerdbot.generator.item;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.awt.image.BufferedImage;
import java.util.List;

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
    private List<BufferedImage> animationFrames;
    private Integer frameDelayMs;

    public InventoryItem(int slot, int amount, String itemName, String extraContent, Integer durabilityPercent) {
        this(new int[]{slot}, new int[]{amount}, itemName, extraContent, durabilityPercent);
    }

    public InventoryItem(int[] slots, int[] amounts, String itemName, String extraContent, Integer durabilityPercent) {
        this.slot = slots;
        this.amount = amounts;
        this.itemName = itemName != null ? itemName.toLowerCase() : null;
        this.extraContent = extraContent;
        this.durabilityPercent = durabilityPercent;
    }
}
