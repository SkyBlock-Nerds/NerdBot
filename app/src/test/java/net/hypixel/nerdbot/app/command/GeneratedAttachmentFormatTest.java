package net.hypixel.nerdbot.app.command;

import net.aerh.imagegenerator.item.GeneratedObject;
import net.dv8tion.jda.api.utils.FileUpload;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A generated render is uploaded as a PNG when static and as a GIF when animated.
 */
class GeneratedAttachmentFormatTest {

    @Test
    void staticRenderIsUploadedAsPng() throws IOException {
        GeneratedObject staticObject = new GeneratedObject(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));

        try (FileUpload upload = GeneratorCommands.renderAttachment(staticObject, "item")) {
            assertEquals("item.png", upload.getName());
        }
    }

    @Test
    void animatedRenderIsUploadedAsGif() throws IOException {
        byte[] gifData = {'G', 'I', 'F', '8', '9', 'a'};
        GeneratedObject animatedObject = new GeneratedObject(
            gifData,
            List.of(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)),
            50
        );

        try (FileUpload upload = GeneratorCommands.renderAttachment(animatedObject, "item")) {
            assertEquals("item.gif", upload.getName());
            assertArrayEquals(gifData, upload.getData().readAllBytes());
        }
    }
}
