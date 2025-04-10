package net.hypixel.nerdbot.util;

import net.hypixel.nerdbot.generator.item.GeneratedObject;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

public class HttpUtil {

    public static ResponseEntity properApiImageReturn(GeneratedObject generatedItem) throws IOException {
        if (generatedItem.isAnimated()) {
            byte[] gifBytes = generatedItem.getGifData();
            ByteArrayResource resource = new ByteArrayResource(gifBytes);

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"image.gif\"")
                .contentType(MediaType.IMAGE_GIF)
                .body(resource);
        } else {
            byte[] imageBytes = ImageUtil.toByteArray(generatedItem.getImage());
            ByteArrayResource resource = new ByteArrayResource(imageBytes);

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"image.png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(resource);
        }
    }
}