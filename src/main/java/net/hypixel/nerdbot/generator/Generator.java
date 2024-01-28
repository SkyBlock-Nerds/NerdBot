package net.hypixel.nerdbot.generator;

import net.hypixel.nerdbot.generator.item.GeneratedItem;

public interface Generator {

    /**
     * Generate an item
     *
     * @return the {@link GeneratedItem generated item}
     */
    GeneratedItem generate();
}
