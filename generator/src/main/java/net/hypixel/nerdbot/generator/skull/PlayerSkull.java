package net.hypixel.nerdbot.generator.skull;

public class PlayerSkull {
    public static final Face[] faces = Face.values();
    private static final double hatLayerOffset = 1.06f;
    public static final double[][] coordinates = new double[][]{
        {1, 1, -1, 1},
        {1, 1, 1, 1},
        {-1, 1, 1, 1},
        {-1, 1, -1, 1},
        {1, -1, -1, 1},
        {1, -1, 1, 1},
        {-1, -1, 1, 1},
        {-1, -1, -1, 1},
        {hatLayerOffset, hatLayerOffset, -hatLayerOffset, 1},
        {hatLayerOffset, hatLayerOffset, hatLayerOffset, 1},
        {-hatLayerOffset, hatLayerOffset, hatLayerOffset, 1},
        {-hatLayerOffset, hatLayerOffset, -hatLayerOffset, 1},
        {hatLayerOffset, -hatLayerOffset, -hatLayerOffset, 1},
        {hatLayerOffset, -hatLayerOffset, hatLayerOffset, 1},
        {-hatLayerOffset, -hatLayerOffset, hatLayerOffset, 1},
        {-hatLayerOffset, -hatLayerOffset, -hatLayerOffset, 1}
    };
}