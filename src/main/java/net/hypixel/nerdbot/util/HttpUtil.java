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
            return ResponseEntity.ok(generatedItem.getGifData()); //TODO: Check if this actually works (don't have a gif item to test (that i know of))
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