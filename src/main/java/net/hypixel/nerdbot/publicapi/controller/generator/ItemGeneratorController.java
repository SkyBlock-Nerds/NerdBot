package net.hypixel.nerdbot.publicapi.controller.generator;

import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.internalapi.generator.GeneratorApi;
import net.hypixel.nerdbot.util.ImageUtil;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Log4j2
@RestController
@RequestMapping("/generator/item")
public class ItemGeneratorController extends BaseGeneratorController {

    @GetMapping("/{itemId}")
    public ResponseEntity generate(
        @PathVariable String itemId,
        @RequestParam(required = false) @Nullable String data,
        @RequestParam(required = false) @Nullable Boolean enchanted,
        @RequestParam(required = false) @Nullable Boolean hoverEffect,
        @RequestParam(required = false) @Nullable String skinValue
    ) {
        try {
            GeneratedObject generatedItem = GeneratorApi.generateItem(itemId, data, enchanted, hoverEffect, skinValue);

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
        } catch (GeneratorException | IOException exception) {
            log.error("Encountered an error while generating an item display", exception);
            return ResponseEntity.status(500).body("An error occurred during image generation: " + exception.getCause());
        }
    }
}