package net.hypixel.nerdbot.generator.skull;

import net.hypixel.nerdbot.generator.item.GeneratedObject;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Arrays;

public class MinecraftHead extends GeneratedObject {
    private static final int DEFAULT_WIDTH = 1250;
    private static final int DEFAULT_HEIGHT = 1250;
    private static final int DEFAULT_RENDER_SCALE = Math.round(Math.min(DEFAULT_WIDTH, DEFAULT_HEIGHT) / 4f);
    private static final double DEFAULT_X_ROTATION = Math.PI / 6;
    private static final double DEFAULT_Y_ROTATION = -Math.PI / 4;
    private static final double DEFAULT_Z_ROTATION = 0f;
    private static final int HEAD_SCALE_DOWN = 2;
    private static final double[][] DEFAULT_VERTEX_COORDINATES;
    private static final double[][] DEFAULT_FACE_ORDER;

    private final BufferedImage skin;
    private final Graphics2D g2d;
    private double xRotation;
    private double yRotation;
    private double zRotation;
    private int renderScaleFactor;

    static {
        DEFAULT_VERTEX_COORDINATES = rotateVerticesAroundAxis(DEFAULT_X_ROTATION, DEFAULT_Y_ROTATION, DEFAULT_Z_ROTATION, DEFAULT_RENDER_SCALE, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        DEFAULT_FACE_ORDER = calculateRenderOrder(DEFAULT_VERTEX_COORDINATES);
    }

    /**
     * Creates a MinecraftHead renderer
     *
     * @param targetSkin the skin which is meant to be created
     */
    public MinecraftHead(BufferedImage targetSkin, int width, int height) {
        super(new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB));
        this.skin = targetSkin;
        this.g2d = super.getImage().createGraphics();

        int invisibilityColor = this.skin.getRGB(32, 0);
        int currentInvisibilityColor = invisibilityColor << 8;

        // checks if the skin's head uses another color for transparency
        if (currentInvisibilityColor != 0) {
            for (int y = 0; y < 32; y++) {
                for (int x = 32; x < 64; x++) {
                    if (invisibilityColor == this.skin.getRGB(x, y))
                        this.skin.setRGB(x, y, 0);
                }
            }
        }
    }

    public MinecraftHead(BufferedImage skin) {
        this(skin, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public MinecraftHead(BufferedImage skin, double xRotation, double yRotation, double zRotation, int width, int height, int renderScaleFactor) {
        this(skin, width, height);

        this.xRotation = xRotation;
        this.yRotation = yRotation;
        this.zRotation = zRotation;

        this.renderScaleFactor = renderScaleFactor;
    }

    /**
     * Rotates the vertices of a shape around the x, y and z axis
     *
     * @param xRotation the angle (in radians) around the up/down axis
     * @param yRotation the angle (in radians) around the left/right axis
     * @param zRotation the angle (in radians) around the into/away from camera axis
     * @param renderScale the scale to enlarge the rendering of the image to
     * @param imageWidth the width of the image
     * @param imageHeight the height of the image
     *
     * @return an array of vertices for the rotated shape
     */
    private static double[][] rotateVerticesAroundAxis(double xRotation, double yRotation, double zRotation, int renderScale, int imageWidth, int imageHeight) {
        double[][] zRotations =  {
            {Math.cos(zRotation), -Math.sin(zRotation), 0, 0},
            {Math.sin(zRotation), Math.cos(zRotation), 0, 0},
            {0, 0, 1, 0},
            {0, 0, 0, 1}
        };
        double[][] yRotations = {
            {Math.cos(yRotation), 0, Math.sin(yRotation), 0},
            {0, 1, 0, 0},
            {-Math.sin(yRotation), 0, Math.cos(yRotation), 0},
            {0, 0, 0, 1}
        };
        double[][] xRotations = {
            {1, 0, 0, 0},
            {0, Math.cos(xRotation), -Math.sin(xRotation), 0},
            {0, Math.sin(xRotation), Math.cos(xRotation), 0},
            {0, 0, 0, 1}
        };
        double[][] scaleFactor = new double[][] {
            {renderScale, 0, 0, 0},
            {0, renderScale, 0, 0},
            {0, 0, 1, 0},
            {0, 0, 0, 1}
        };
        double[][] offset = new double[][] {
            {1, 0, 0, imageWidth / 2f},
            {0, 1, 0, imageHeight / 2f},
            {0, 0, 1, 0},
            {0, 0, 0, 0}
        };

        double[][] shapeVertices = PlayerSkull.coordinates;
        double[][] result = new double[shapeVertices.length][4];
        for (var i = 0; i < shapeVertices.length; i++) {
            result[i] = multiplyMatrix(zRotations, shapeVertices[i]);
            result[i] = multiplyMatrix(yRotations, result[i]);
            result[i] = multiplyMatrix(xRotations, result[i]);
            result[i] = multiplyMatrix(scaleFactor, result[i]);
            result[i] = multiplyMatrix(offset, result[i]);
        }

        return result;
    }

    /**
     * Calculates the order in which faces should be rendered of a shape from back to front
     *
     * @param vertices Vertices of the shape to be rendered
     *
     * @return returns an ordered array of faces from the furthest distance to camera to closest
     */
    private static double[][] calculateRenderOrder(double[][] vertices) {
        Face[] faces = PlayerSkull.faces;
        double[][] order = new double[faces.length][2];

        for (int i = 0; i < faces.length; i++) {
            double average = 0;
            Face currentFace = faces[i];
            for (int point : currentFace.getFaceVertices()) {
                average += vertices[point][2];
            }
            order[i] = new double[] {i, average / 4.0};
        }

        Arrays.sort(order, (face1, face2) -> {
            if (face1[1] == face2[1])
                return 0;
            return face1[1] < face2[1] ? 1 : -1;
        });
        return order;
    }

    /**
     * Multiplies two coordinate matrices together
     *
     * @param matrix 1x4 matrix to modify the position of a coordinate
     * @param vertexPos 1x4 matrix describing the points coordinates in space
     *
     * @return multiplied matrix
     */
    private static double[] multiplyMatrix(double[][] matrix, double[] vertexPos) {
        double[] calculatedMatrix = new double[4];

        for (int row = 0; row < matrix.length; row++) {
            double[] currentRow = matrix[row];
            double cellResult = 0;

            for (var col = 0; col < vertexPos.length; col++) {
                cellResult += currentRow[col] * vertexPos[col];
            }
            calculatedMatrix[row] = cellResult;
        }

        return calculatedMatrix;
    }

    /**
     * Renders the Minecraft Head
     *
     * @param vertexCoordinates an array of vertices of a shape in the format of [x, y, z, 1]
     * @param faceOrder an ordered array of faces from faces furthest from camera to closest in the format of [faceIndex, distanceFromCamera]
     */
    private void drawHead(double[][] vertexCoordinates, double[][] faceOrder) {
        for (int face = 0; face < PlayerSkull.faces.length; face++) {
            int faceIndex = (int) faceOrder[face][0];
            Face currentFace = PlayerSkull.faces[faceIndex];

            int[] facePoints = currentFace.getFaceVertices();
            double[] vertex1 = vertexCoordinates[facePoints[0]];
            double[] vertex2 = vertexCoordinates[facePoints[1]];
            double[] vertex3 = vertexCoordinates[facePoints[2]];
            double[] vertex4 = vertexCoordinates[facePoints[3]];

            // calculating the x/y movements to draw a cube from a start point
            double[][] startPointDisplacement = new double[][]{
                {(vertex1[0] - vertex2[0]) / 8, (vertex1[1] - vertex2[1]) / 8},
                {(vertex2[0] - vertex3[0]) / 8, (vertex2[1] - vertex3[1]) / 8},
                {(vertex3[0] - vertex4[0]) / 8, (vertex3[1] - vertex4[1]) / 8}
            };
            double xOffset = (vertex4[0] - vertex1[0]) / 8;

            int uvFaceX = currentFace.getStartX();
            int uvFaceY = currentFace.getStartY();
            float shadow = currentFace.getShadow() / 255f;

            for (var y = 0; y < 8; y++) {
                for (var x = 0; x < 8; x++) {
                    int color = this.skin.getRGB(x + uvFaceX, y + uvFaceY);
                    // skip painting if there is nothing to paint
                    int alpha = ((color >> 24) & 0xff);
                    if (alpha == 0) {
                        continue;
                    }

                    // applying shadows to the face
                    int red = Math.round(((color >> 16) & 0xff) * shadow);
                    int green = Math.round(((color >> 8) & 0xff) * shadow);
                    int blue = Math.round((color & 0xff) * shadow);
                    g2d.setColor(new Color(red, green, blue, alpha));

                    // calculating the initial starting point (first index of face) for a face
                    double xCoordinate = vertex1[0] - startPointDisplacement[0][0] * x + xOffset * y;
                    double yCoordinate = vertex1[1] - startPointDisplacement[0][1] * x - startPointDisplacement[1][1] * y;

                    // applying x/y transforms for a face
                    int[] pointsX = new int[4];
                    int[] pointsY = new int[4];

                    pointsX[0] = (int) Math.round(xCoordinate);
                    pointsY[0] = (int) Math.round(yCoordinate);
                    for (int i = 0; i < 3; i++) {
                        xCoordinate -= startPointDisplacement[i][0];
                        yCoordinate -= startPointDisplacement[i][1];
                        pointsX[i + 1] = (int) Math.round(xCoordinate);
                        pointsY[i + 1] = (int) Math.round(yCoordinate);
                    }

                    g2d.drawPolygon(pointsX, pointsY, 4);
                    g2d.fillPolygon(pointsX, pointsY, 4);
                }
            }
        }
    }

    public MinecraftHead generate() {
        if (renderScaleFactor == 0) {
            drawHead(DEFAULT_VERTEX_COORDINATES, DEFAULT_FACE_ORDER);
        } else {
            double[][] vertexCoordinates = rotateVerticesAroundAxis(this.xRotation, this.yRotation, this.zRotation, this.renderScaleFactor, this.image.getWidth(), this.image.getHeight());
            double[][] faceRenderOrder = calculateRenderOrder(vertexCoordinates);
            drawHead(vertexCoordinates, faceRenderOrder);
        }

        g2d.dispose();
        return this;
    }

    /**
     * Gets the generated image
     *
     * @return the buffered image containing the head
     */
    @Override
    public BufferedImage getImage() {
        return generate().scaleHead();
    }

    private BufferedImage scaleHead() {
        Image rescaledImage = image.getScaledInstance(image.getWidth() / HEAD_SCALE_DOWN, image.getHeight() / HEAD_SCALE_DOWN, Image.SCALE_AREA_AVERAGING);
        BufferedImage finalHead = new BufferedImage(rescaledImage.getWidth(null), rescaledImage.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        // Draw the image on to the buffered image
        Graphics2D rescaledGraphics = finalHead.createGraphics();
        rescaledGraphics.drawImage(rescaledImage, 0, 0, null);
        rescaledGraphics.dispose();

        return finalHead;
    }
}
