package net.hypixel.nerdbot.generator.builder;

public interface ClassBuilder<T> {

    /**
     * Build a new instance of the given {@link T class}.
     *
     * @return The new instance of the given {@link T class}.
     */
    T build();
}
