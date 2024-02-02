package net.hypixel.nerdbot.generator.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.image.OldMinecraftInventory;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.item.RecipeItem;
import net.hypixel.nerdbot.generator.parser.recipe.RecipeStringParser;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.Map;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MinecraftRecipeGenerator implements Generator {

    private final String recipeString;
    private final boolean renderBackground;

    @Override
    public GeneratedObject generate() {
        return new GeneratedObject(buildRecipe(recipeString, renderBackground));
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
        if (!OldMinecraftInventory.resourcesRegistered()) {
            throw new GeneratorException("Textures not loaded correctly");
        }

        RecipeStringParser parser = new RecipeStringParser();
        Map<Integer, RecipeItem> items = parser.parse(recipeString);

        for (RecipeItem parsedItem : items.values()) {
            if (parsedItem.getItemName().contains("player_head")) {
                MinecraftPlayerHeadGenerator playerHeadGenerator = new MinecraftPlayerHeadGenerator.Builder()
                    .withSkin(parsedItem.getExtraContent())
                    .build();
                BufferedImage playerHeadImage = playerHeadGenerator.generate().getImage();

                parsedItem.setImage(playerHeadImage);
                continue;
            }

            MinecraftItemGenerator itemGenerator = new MinecraftItemGenerator.Builder()
                .withItem(parsedItem.getItemName())
                .isEnchanted(parsedItem.getExtraContent() != null && parsedItem.getExtraContent().equalsIgnoreCase("enchant"))
                .build();

            BufferedImage itemImage = itemGenerator.generate().getImage();
            parsedItem.setImage(itemImage);
        }

        OldMinecraftInventory inventory = new OldMinecraftInventory(items, renderBackground).render();
        return inventory.getImage();
    }
}
