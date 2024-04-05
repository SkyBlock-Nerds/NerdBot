package net.hypixel.nerdbot.generator.skull;

import lombok.Getter;

@Getter
public enum Face {
    HAT_FRONT(new int[]{15, 12, 8, 11}, 111, 40, 8),
    HAT_RIGHT(new int[]{12, 13, 9, 8}, 156, 48, 8),
    HAT_BACK(new int[]{13, 14, 10, 9}, 156, 56, 8),
    HAT_LEFT(new int[]{14, 15, 11, 10}, 162, 32, 8),
    HAT_TOP(new int[]{14, 13, 12, 15}, 255, 40, 0),
    HAT_BOTTOM(new int[]{10, 9, 8, 11}, 111, 48, 0),
    HEAD_FRONT(new int[]{7, 4, 0, 3}, 111, 8, 8),
    HEAD_RIGHT(new int[]{4, 5, 1, 0}, 156, 16, 8),
    HEAD_BACK(new int[]{5, 6, 2, 1}, 156, 24, 8),
    HEAD_LEFT(new int[]{6, 7, 3, 2}, 162, 0, 8),
    HEAD_TOP(new int[]{6, 5, 4, 7}, 255, 8, 0),
    HEAD_BOTTOM(new int[]{2, 1, 0, 3}, 111, 16, 0);

    private final int[] faceVertices;
    private final int shadow;
    private final int startX;
    private final int startY;

    Face(int[] faceVertices, int shadow, int startX, int startY) {
        this.faceVertices = faceVertices;
        this.shadow = shadow;
        this.startX = startX;
        this.startY = startY;
    }
}