package net.hypixel.nerdbot.generator;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class MinecraftHead {
    private final static int HEAD_SCALE_UP = 3;
    private final static int HEAD_SCALE_DOWN = 2;
    private BufferedImage image;
    private final BufferedImage skin;
    private final Graphics2D g2d;

    /***
     * Creates a MinecraftHead renderer
     * @param targetSkin the skin which is meant to be created
     */
    public MinecraftHead(BufferedImage targetSkin) {
        int width = (int) Math.round(17 * HeadTransforms.xDistanceHat);
        int height = (int) Math.round(17 * HeadTransforms.squareHatDistance);

        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        this.g2d = this.image.createGraphics();
        this.skin = targetSkin;
    }

    /***
     * Paints a single square on the image given the lower left point
     * @param x starting x location to draw
     * @param y starting y location to draw
     * @param side the direction the square is facing
     */
    private void paintSquare(double x, double y, Side side) {
        int[] pointsX = new int[4];
        int[] pointsY = new int[4];
        double[] transforms = side.getTransforms();

        for (int i = 0; i < 4; i++) {
            pointsX[i] = (int) Math.round(x + transforms[i * 2]);
            pointsY[i] = (int) Math.round(y + transforms[i * 2 + 1]);
        }

        g2d.fillPolygon(pointsX, pointsY, 4);
    }

    /***
     * Draws an entire face of the skin onto the image
     * @param startingX starting x location to draw (the center vertex of the cube)
     * @param startingY starting y location to draw (the center vertex of the cube)
     * @param side the direction the square is facing
     * @param face which part of the head (top, bottom, left, right, etc)
     */
    private void drawFace(double startingX, double startingY, Side side, Face face) {
        int newLineDisplacement = 0;
        double pixelTrackUp;
        double sideDistance = side.getDistance();
        double xDistance = side.getXDistance();
        double yDistance = side.getYDistance();

        // applys transforms to the starting X/Y position to ensure it is in the correct place
        switch (side) {
            case RIGHT_SIDE, RIGHT_HAT_SIDE -> {
                pixelTrackUp = sideDistance;
                startingY += sideDistance * 7;
            }
            case LEFT_SIDE, LEFT_HAT_SIDE -> {
                pixelTrackUp = sideDistance;
                startingX -= xDistance * 8;
                startingY += yDistance * 6;
                yDistance = -yDistance;
            }
            case TOP_SIDE, TOP_HAT_SIDE -> {
                pixelTrackUp = yDistance;
                newLineDisplacement = -1;
                startingY -= sideDistance;
            }
            default -> {
                pixelTrackUp = 0;
            }
        }

        boolean isFlipped = false;
        switch (face) {
            case HEAD_BACK, HAT_BACK, HEAD_RIGHT, HAT_RIGHT -> {
                isFlipped = true;
            }
            default -> {}
        }
        int increment = isFlipped ? -1 : 1;

        double defaultX = startingX;
        double defaultY = startingY;

        for (int y = face.getStartY() + 7; y >= face.getStartY(); y--) {
            int imageX = face.getStartX() + (isFlipped ? 7 : 0);
            for (int x = 0; x < 8; x++) {
                g2d.setColor(new Color(skin.getRGB(imageX, y), true));
                paintSquare(startingX, startingY, side);
                startingX += xDistance;
                startingY -= yDistance;

                imageX += increment;
            }
            defaultX += newLineDisplacement * xDistance;
            startingX = defaultX;
            defaultY -= pixelTrackUp;
            startingY = defaultY;
        }
    }

    /***
     * Renders the Minecraft Head
     * @return a rendered Minecraft Head
     */
    public MinecraftHead drawHead() {
        int startingX = this.image.getWidth() / 2;
        int startingY = this.image.getHeight() / 2;

        drawFace(startingX, startingY + HeadTransforms.squareHatDistance * 8, Side.TOP_HAT_SIDE, Face.HAT_BOTTOM);
        drawFace(startingX - HeadTransforms.xDistanceHat * 8, startingY - HeadTransforms.yDistanceHat * 8, Side.RIGHT_HAT_SIDE, Face.HAT_BACK);
        drawFace(startingX + HeadTransforms.xDistanceHat * 8, startingY - HeadTransforms.yDistanceHat * 8, Side.LEFT_HAT_SIDE, Face.HAT_RIGHT);

        drawFace(startingX, startingY, Side.TOP_SIDE, Face.HEAD_TOP);
        drawFace(startingX, startingY, Side.RIGHT_SIDE,  Face.HEAD_FRONT);
        drawFace(startingX, startingY, Side.LEFT_SIDE, Face.HEAD_LEFT);

        drawFace(startingX, startingY, Side.TOP_HAT_SIDE, Face.HAT_TOP);
        drawFace(startingX, startingY, Side.RIGHT_HAT_SIDE, Face.HAT_FRONT);
        drawFace(startingX, startingY, Side.LEFT_HAT_SIDE, Face.HAT_LEFT);

        g2d.dispose();

        return this;
    }

    /***
     * Gets the generated image
     * @return the buffered image containing the head
     */
    public BufferedImage getImage(boolean smoothHead) {
        if (smoothHead) {
            return scaleHead();
        }
        return this.image;
    }

    private BufferedImage scaleHead() {
        Image rescaledImage = image.getScaledInstance(image.getWidth() * HEAD_SCALE_UP, image.getHeight() * HEAD_SCALE_UP, Image.SCALE_SMOOTH).getScaledInstance(image.getWidth() / HEAD_SCALE_DOWN, image.getHeight() / HEAD_SCALE_DOWN, Image.SCALE_AREA_AVERAGING);
        BufferedImage finalHead = new BufferedImage(rescaledImage.getWidth(null), rescaledImage.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        // Draw the image on to the buffered image
        Graphics2D rescaledGraphics = finalHead.createGraphics();
        rescaledGraphics.drawImage(rescaledImage, 0, 0, null);
        rescaledGraphics.dispose();

        return finalHead;
    }

    /***
     * Saves the image to a file
     * @return a file which can be shared
     * @throws IOException If the file cannot be saved
     */
    public File toFile(boolean smoothHead) throws IOException {
        File tempFile = File.createTempFile("image", ".png");
        ImageIO.write(getImage(smoothHead), "PNG", tempFile);
        return tempFile;
    }
}

/***
 * Standard distances between points on the isometric grid.
 */
class HeadTransforms {
    public static int squareDistance = 33;
    public static double xDistance = squareDistance * Math.cos(Math.toRadians(30));
    public static double yDistance = squareDistance * Math.sin(Math.toRadians(30));
    public static double squareHatDistance = squareDistance * 1.07;
    public static double xDistanceHat = squareHatDistance * Math.cos(Math.toRadians(30));
    public static double yDistanceHat = squareHatDistance * Math.sin(Math.toRadians(30));
}

/***
 * Describes the 4 points of the square which fit the isometric pattern
 */
enum Side {
    LEFT_SIDE(HeadTransforms.squareDistance, new double[] {0, 0, 0, HeadTransforms.squareDistance, HeadTransforms.xDistance, HeadTransforms.squareDistance + HeadTransforms.yDistance, HeadTransforms.xDistance, HeadTransforms.yDistance}, HeadTransforms.xDistance, HeadTransforms.yDistance),
    RIGHT_SIDE(HeadTransforms.squareDistance, new double[] {0, 0, 0, HeadTransforms.squareDistance, HeadTransforms.xDistance, HeadTransforms.squareDistance - HeadTransforms.yDistance, HeadTransforms.xDistance, -HeadTransforms.yDistance}, HeadTransforms.xDistance, HeadTransforms.yDistance),
    TOP_SIDE(HeadTransforms.squareDistance, new double[] {0, 0, -HeadTransforms.xDistance, HeadTransforms.yDistance, 0, HeadTransforms.squareDistance, HeadTransforms.xDistance, HeadTransforms.yDistance}, HeadTransforms.xDistance, HeadTransforms.yDistance),
    LEFT_HAT_SIDE(HeadTransforms.squareHatDistance, new double[] {0, 0, 0, HeadTransforms.squareHatDistance, HeadTransforms.xDistanceHat, HeadTransforms.squareHatDistance + HeadTransforms.yDistanceHat, HeadTransforms.xDistanceHat, HeadTransforms.yDistanceHat}, HeadTransforms.xDistanceHat, HeadTransforms.yDistanceHat),
    RIGHT_HAT_SIDE(HeadTransforms.squareHatDistance, new double[] {0, 0, 0, HeadTransforms.squareHatDistance, HeadTransforms.xDistanceHat, HeadTransforms.squareHatDistance - HeadTransforms.yDistanceHat, HeadTransforms.xDistanceHat, -HeadTransforms.yDistanceHat}, HeadTransforms.xDistanceHat, HeadTransforms.yDistanceHat),
    TOP_HAT_SIDE(HeadTransforms.squareHatDistance, new double[] {0, 0, -HeadTransforms.xDistanceHat, HeadTransforms.yDistanceHat, 0, HeadTransforms.squareHatDistance, HeadTransforms.xDistanceHat, HeadTransforms.yDistanceHat}, HeadTransforms.xDistanceHat, HeadTransforms.yDistanceHat);
    private final double distance;
    private final double[] transforms;
    private final double xDistance;
    private final double yDistance;

    Side(double distance, double[] transforms, double xDistance, double yDistance) {
        this.distance = distance;
        this.transforms = transforms;
        this.xDistance = xDistance;
        this.yDistance = yDistance;
    }

    public double getDistance() {
        return distance;
    }
    public double[] getTransforms() {
        return this.transforms;
    }
    public double getXDistance() {
        return this.xDistance;
    }
    public double getYDistance() {
        return this.yDistance;
    }
}

/***
 * The X/Y coordinates for where the head is located in the skin image
 */
enum Face {
    HEAD_FRONT(8, 8), HEAD_BACK(24, 8), HEAD_LEFT(0, 8), HEAD_RIGHT(16, 8), HEAD_TOP(8, 0), HEAD_BOTTOM(16, 0),
    HAT_FRONT(40, 8), HAT_BACK(56, 8), HAT_LEFT(32, 8), HAT_RIGHT(48, 8), HAT_TOP(40, 0), HAT_BOTTOM(48, 0);

    private final int startX, startY;

    Face(int startX, int startY) {
        this.startX = startX;
        this.startY = startY;
    }

    public int getStartX() {
        return this.startX;
    }

    public int getStartY() {
        return this.startY;
    }
}
