package net.hypixel.nerdbot.publicapi.controllers;

import net.hypixel.nerdbot.command.GeneratorCommands;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ItemGeneratorController {

    @GetMapping("/generate-item")
    public String generateItem() {
        return ;
    }
}