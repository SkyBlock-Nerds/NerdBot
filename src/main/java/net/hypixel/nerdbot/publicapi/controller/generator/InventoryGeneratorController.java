package net.hypixel.nerdbot.publicapi.controller.generator;

import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.internalapi.generator.GeneratorApi;
import net.hypixel.nerdbot.util.HttpUtil;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Log4j2
@RestController
@RequestMapping("/generator/inventory")
public class InventoryGeneratorController extends BaseGeneratorController {

    @GetMapping("")
    public ResponseEntity generate(
        @RequestParam String inventoryString,
        @RequestParam int rows,
        @RequestParam int slotsPerRow,
        @RequestParam(required = false) @Nullable String hoveredItemString,
        @RequestParam(required = false) @Nullable String containerName,
        @RequestParam(required = false) @Nullable Boolean drawBorder
    ) {
        try {
            return HttpUtil.properApiImageReturn(GeneratorApi.generateInventory(inventoryString, rows, slotsPerRow, hoveredItemString, containerName, drawBorder));
        } catch (GeneratorException | IOException exception) {
            log.error("Encountered an error while generating the image", exception);
            return ResponseEntity.status(500).body("An error occurred during image generation: " + exception.getCause());
        }
    }
}