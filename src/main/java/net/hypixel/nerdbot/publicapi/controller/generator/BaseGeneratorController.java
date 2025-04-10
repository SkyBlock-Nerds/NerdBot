package net.hypixel.nerdbot.publicapi.controller.generator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

public abstract class BaseGeneratorController {

    @GetMapping("/info")
    public ResponseEntity info() {
        return ResponseEntity.status(501).body("This endpoint is not implemented yet"); //TODO
    }
}