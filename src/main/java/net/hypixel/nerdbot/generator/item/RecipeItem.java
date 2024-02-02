package net.hypixel.nerdbot.generator.item;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class RecipeItem extends GeneratedObject {

    private int slot;
    private int amount;
    private String itemName;
    private String extraContent;

    public RecipeItem(int slot, int amount, String itemName, String extraContent) {
        super(null);

        this.slot = slot;
        this.amount = amount;
        this.itemName = itemName;
        this.extraContent = extraContent;
    }
}
