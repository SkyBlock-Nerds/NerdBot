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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Log4j2
@RestController
@RequestMapping("/generator/tooltip")
public class TooltipGeneratorController extends BaseGeneratorController{

    @GetMapping("")
    public ResponseEntity generate(
        @RequestParam String itemName,
        @RequestParam String itemLore,
        @RequestParam(required = false) @Nullable String type,
        @RequestParam(required = false) @Nullable String rarity,
        @RequestParam(required = false) @Nullable String itemId,
        @RequestParam(required = false) @Nullable String skinValue,
        @RequestParam(required = false) @Nullable String recipe,
        @RequestParam(required = false) @Nullable Integer alpha,
        @RequestParam(required = false) @Nullable Integer padding,
        @RequestParam(required = false) @Nullable Boolean disableRarityLineBreak,
        @RequestParam(required = false) @Nullable Boolean enchanted,
        @RequestParam(required = false) @Nullable Boolean centered,
        @RequestParam(required = false) @Nullable Boolean paddingFirstLine,
        @RequestParam(required = false) @Nullable Integer maxLineLength,
        @RequestParam(required = false) @Nullable String tooltipSide,
        @RequestParam(required = false) @Nullable Boolean renderBorder
    ) {
        try {
            GeneratedObject generatedItem = GeneratorApi.generateTooltip(itemName, itemLore, type, rarity, itemId, skinValue, recipe, alpha, padding, disableRarityLineBreak, enchanted, centered, paddingFirstLine, maxLineLength, tooltipSide, renderBorder);

            byte[] imageBytes = ImageUtil.toByteArray(generatedItem.getImage());
            ByteArrayResource resource = new ByteArrayResource(imageBytes);

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"image.png\"")
                .contentType(MediaType.IMAGE_PNG)
                .body(resource);
        } catch (GeneratorException | IOException exception) {
            log.error("Encountered an error while generating the image", exception);
            return ResponseEntity.status(500).body("An error occurred during image generation: " + exception.getCause());
        }
    }
}