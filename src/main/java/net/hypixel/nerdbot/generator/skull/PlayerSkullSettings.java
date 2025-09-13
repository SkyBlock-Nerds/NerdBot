package net.hypixel.nerdbot.generator.skull;

public class PlayerSkullSettings {
    public static final Face[] FACES = Face.values();
    public static final double HAT_LAYER_OFFSET = 1.06f;
    public static final double[][] COORDINATES = new double[][]{
        {1, 1, -1, 1},
        {1, 1, 1, 1},
        {-1, 1, 1, 1},
        {-1, 1, -1, 1},
        {1, -1, -1, 1},
        {1, -1, 1, 1},
        {-1, -1, 1, 1},
        {-1, -1, -1, 1},
        {HAT_LAYER_OFFSET, HAT_LAYER_OFFSET, -HAT_LAYER_OFFSET, 1},
        {HAT_LAYER_OFFSET, HAT_LAYER_OFFSET, HAT_LAYER_OFFSET, 1},
        {-HAT_LAYER_OFFSET, HAT_LAYER_OFFSET, HAT_LAYER_OFFSET, 1},
        {-HAT_LAYER_OFFSET, HAT_LAYER_OFFSET, -HAT_LAYER_OFFSET, 1},
        {HAT_LAYER_OFFSET, -HAT_LAYER_OFFSET, -HAT_LAYER_OFFSET, 1},
        {HAT_LAYER_OFFSET, -HAT_LAYER_OFFSET, HAT_LAYER_OFFSET, 1},
        {-HAT_LAYER_OFFSET, -HAT_LAYER_OFFSET, HAT_LAYER_OFFSET, 1},
        {-HAT_LAYER_OFFSET, -HAT_LAYER_OFFSET, -HAT_LAYER_OFFSET, 1}
    };

    public static final int DEFAULT_WIDTH = 1250;
    public static final int DEFAULT_HEIGHT = 1250;
    public static final int DEFAULT_RENDER_SCALE = Math.round(Math.min(DEFAULT_WIDTH, DEFAULT_HEIGHT) / 4f);
    public static final double DEFAULT_X_ROTATION = Math.PI / 6;
    public static final double DEFAULT_Y_ROTATION = -Math.PI / 4;
    public static final double DEFAULT_Z_ROTATION = 0f;
    public static final int HEAD_SCALE_DOWN = 3;
}
