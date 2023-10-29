package net.hypixel.nerdbot.generator;

import net.hypixel.nerdbot.generator.util.Item;

public interface Generator {

    /**
     * Generate an item
     *
     * @return the generated item
     */
    Item generate();
}
