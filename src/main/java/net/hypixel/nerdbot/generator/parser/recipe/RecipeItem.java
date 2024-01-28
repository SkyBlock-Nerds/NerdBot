package net.hypixel.nerdbot.generator.parser.recipe;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.nerdbot.generator.GeneratedItem;

@Getter
@Setter
@ToString
public class RecipeItem extends GeneratedItem {

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
