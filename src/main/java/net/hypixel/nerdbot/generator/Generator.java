package net.hypixel.nerdbot.generator;

public interface Generator {

    /**
     * Generate an item
     *
     * @return the {@link GeneratedItem generated item}
     */
    GeneratedItem generate();
}
