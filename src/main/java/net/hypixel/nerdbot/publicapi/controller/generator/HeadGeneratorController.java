package net.hypixel.nerdbot.publicapi.controller.generator;

import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.internalapi.generator.GeneratorApi;
import net.hypixel.nerdbot.util.HttpUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Log4j2
@RestController
@RequestMapping("/generator/head")
public class HeadGeneratorController extends BaseGeneratorController {

    @GetMapping("/{texture}")
    public ResponseEntity generate(
        @PathVariable String texture
    ) {
        try {
            return HttpUtil.properApiImageReturn(GeneratorApi.generateHead(texture));
        } catch (GeneratorException | IOException exception) {
            log.error("Encountered an error while generating the image", exception);
            return ResponseEntity.status(500).body("An error occurred during image generation: " + exception.getCause());
        }
    }

    @GetMapping("")
    public ResponseEntity generate() {
        return generate("");
    }
}