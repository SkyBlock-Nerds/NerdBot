package net.hypixel.nerdbot.generator.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.hypixel.nerdbot.generator.ClassBuilder;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.parser.old.RecipeParser;
import net.hypixel.nerdbot.generator.util.GeneratorMessages;
import net.hypixel.nerdbot.generator.GeneratedItem;
import net.hypixel.nerdbot.generator.util.MinecraftInventory;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MinecraftRecipeGenerator implements Generator {

    private final String recipeString;
    private final boolean renderBackground;

    @Override
    public GeneratedItem generate() {
        return new GeneratedItem(buildRecipe(recipeString, renderBackground));
    }

    public static class Builder implements ClassBuilder<MinecraftRecipeGenerator> {
        private String recipeString;
        private boolean renderBackground;

        public MinecraftRecipeGenerator.Builder withRecipeString(String recipeString) {
            this.recipeString = recipeString;
            return this;
        }

        public MinecraftRecipeGenerator.Builder renderBackground(boolean renderBackground) {
            this.renderBackground = renderBackground;
            return this;
        }

        @Override
        public MinecraftRecipeGenerator build() {
            return new MinecraftRecipeGenerator(recipeString, renderBackground);
        }
    }

    @Nullable
    public BufferedImage buildRecipe(String recipeString, boolean renderBackground) {
        if (!MinecraftInventory.resourcesRegistered()) {
            throw new GeneratorException(GeneratorMessages.ITEM_RESOURCE_NOT_LOADED);
        }

        RecipeParser parser = new RecipeParser();
        parser.parseRecipe(recipeString);

        if (!parser.isSuccessfullyParsed()) {
            throw new GeneratorException(GeneratorMessages.RECIPE_NOT_PARSED);
        }

        // Iterate through all items and generate an item using the MinecraftItemGenerator
        for (RecipeParser.RecipeItem item : parser.getRecipeData().values()) {
            if (item.getItemName().equalsIgnoreCase("player_head")) {
                MinecraftPlayerHeadGenerator playerHeadGenerator = new MinecraftPlayerHeadGenerator.Builder().withSkin(item.getExtraDetails()).build();
                BufferedImage playerHeadImage = playerHeadGenerator.generate().getImage();

                if (playerHeadImage == null) {
                    return null;
                }

                item.setImage(playerHeadImage);
                continue;
            }

            MinecraftItemGenerator itemGenerator = new MinecraftItemGenerator.Builder()
                .withItem(item.getItemName())
                .isEnchanted(item.getExtraDetails().equalsIgnoreCase("enchant"))
                .build();

            BufferedImage itemImage = itemGenerator.generate().getImage();

            if (itemImage == null) {
                return null;
            }

            item.setImage(itemImage);
        }

        MinecraftInventory inventory = new MinecraftInventory(parser.getRecipeData(), renderBackground).render();
        return inventory.getImage();
    }
}
