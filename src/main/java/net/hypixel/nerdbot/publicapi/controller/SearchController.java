package net.hypixel.nerdbot.publicapi.controller;

import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.internalapi.generator.GeneratorApi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Log4j2
@RestController
@RequestMapping("/search")
public class SearchController {

    @GetMapping("/ItemId")
    public ResponseEntity itemIds() {
        return ResponseEntity.ok(GeneratorApi.itemNamesAutoCompletes());
    }

    @GetMapping("/itemId/{searchTerm}")
    public ResponseEntity itemIds(@PathVariable String searchTerm) {
        return ResponseEntity.ok(contains(GeneratorApi.itemNamesAutoCompletes(), searchTerm));
    }

    @GetMapping("/rarity")
    public ResponseEntity itemRarities() {
        return ResponseEntity.ok(GeneratorApi.itemRaritiesAutoCompletes());
    }

    @GetMapping("/rarity/{searchTerm}")
    public ResponseEntity itemRarities(@PathVariable String searchTerm) {
        return ResponseEntity.ok(contains(GeneratorApi.itemRaritiesAutoCompletes(), searchTerm));
    }

    @GetMapping("/tooltipside")
    public ResponseEntity tooltipSide() {
        return ResponseEntity.ok(GeneratorApi.tooltipSideAutoCompletes());
    }

    @GetMapping("/tooltipside/{searchTerm}")
    public ResponseEntity tooltipSide(@PathVariable String searchTerm) {
        return ResponseEntity.ok(contains(GeneratorApi.tooltipSideAutoCompletes(), searchTerm));
    }

    private static List<String> contains(List<String> set, String searchTerm) {
        return set
            .stream()
            .filter(itemName -> itemName.toLowerCase().contains(searchTerm.toLowerCase()))
            .toList();
    }
}