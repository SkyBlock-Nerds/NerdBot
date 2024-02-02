package net.hypixel.nerdbot.generator;

import net.hypixel.nerdbot.generator.item.GeneratedObject;

public interface Generator {

    /**
     * Generate an item
     *
     * @return the {@link GeneratedObject generated item}
     */
    GeneratedObject generate();
}
