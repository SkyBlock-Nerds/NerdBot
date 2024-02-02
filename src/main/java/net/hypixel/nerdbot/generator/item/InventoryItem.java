package net.hypixel.nerdbot.generator.item;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.generator.image.ImageCoordinates;

import java.awt.image.BufferedImage;

@Getter
@Setter
@ToString
public class InventoryItem extends GeneratedObject {

    private int overallSlot;
    private int slotInRow;
    private int row;
    private ImageCoordinates imageCoordinates;

    public InventoryItem(int overallSlot, int slotInRow, int row, ImageCoordinates imageCoordinates) {
        super(null);
        this.overallSlot = overallSlot;
        this.slotInRow = slotInRow;
        this.row = row;
        this.imageCoordinates = imageCoordinates;
    }

    public InventoryItem(int overallSlot, int slotInRow, int row, ImageCoordinates imageCoordinates, BufferedImage image) {
        super(image);
        this.overallSlot = overallSlot;
        this.slotInRow = slotInRow;
        this.row = row;
        this.imageCoordinates = imageCoordinates;
    }
}
