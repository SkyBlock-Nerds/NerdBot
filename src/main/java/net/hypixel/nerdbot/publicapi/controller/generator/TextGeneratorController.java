package net.hypixel.nerdbot.publicapi.controller.generator;

import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.internalapi.generator.GeneratorApi;
import net.hypixel.nerdbot.util.HttpUtil;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Log4j2
@RestController
@RequestMapping("/generator/text")
public class TextGeneratorController extends BaseGeneratorController{

    @GetMapping("/{text}")
    public ResponseEntity generate(
        @PathVariable String text,
        @RequestParam(required = false) @Nullable Boolean centered,
        @RequestParam(required = false) @Nullable Integer alpha,
        @RequestParam(required = false) @Nullable Integer padding,
        @RequestParam(required = false) @Nullable Integer maxLineLength,
        @RequestParam(required = false) @Nullable Boolean renderBorder
    ) {
        try {
            return HttpUtil.properApiImageReturn(GeneratorApi.generateText(text, centered, alpha, padding, maxLineLength, renderBorder));
        } catch (GeneratorException | IOException exception) {
            log.error("Encountered an error while generating the image", exception);
            return ResponseEntity.status(500).body("An error occurred during image generation: " + exception.getCause());
        }
    }
}