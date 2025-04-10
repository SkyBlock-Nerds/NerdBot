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
@RequestMapping("/generator/dialogue/multi")
public class MultiDialogueGeneratorController extends BaseGeneratorController {

    @GetMapping("")
    public ResponseEntity generate(
        @RequestParam String npcNames,
        @RequestParam String dialogue,
        @RequestParam(required = false) @Nullable Integer maxLineLength,
        @RequestParam(required = false) @Nullable Boolean abiphone,
        @RequestParam(required = false) @Nullable String skinValue
    ) {
        try {
            return HttpUtil.properApiImageReturn(GeneratorApi.generateMultiDialogue(npcNames, dialogue, maxLineLength, abiphone, skinValue));
        } catch (GeneratorException | IOException exception) {
            log.error("Encountered an error while generating the image", exception);
            return ResponseEntity.status(500).body("An error occurred during image generation: " + exception.getCause());
        }
    }
}