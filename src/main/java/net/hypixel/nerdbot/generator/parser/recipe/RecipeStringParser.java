package net.hypixel.nerdbot.generator.parser.recipe;

import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.parser.Parser;
import net.hypixel.nerdbot.util.Range;

import java.util.HashMap;
import java.util.Map;

public class RecipeStringParser implements Parser<Map<Integer, RecipeItem>> {

    /**
     * Parses a string into a map of recipe items.
     * <br>
     * NOTE: Slots are 1-indexed.
     *
     * @param input The string to parse.
     *
     * @return A map of {@link RecipeItem} objects to their respective slots.
     */
    @Override
    public Map<Integer, RecipeItem> parse(String input) {
        Map<Integer, RecipeItem> result = new HashMap<>();
        String[] split = input.split("%%");

        for (String item : split) {
            item = item.trim();
            String[] components = item.split(",");

            try {
                Integer.parseInt(components[0]);
            } catch (NumberFormatException exception) {
                throw new GeneratorException("Invalid slot: " + components[0] + " in item: `" + item + "`");
            }

            int slot = Integer.parseInt(components[0]);
            String material = components[1];
            String data = components.length > 2 ? components[2] : null;
            int amount = 1;

            if (material.contains(":")) {
                split = material.split(":");

                try {
                    amount = Range.between(1, 64).fit(Integer.parseInt(split[1]));
                } catch (NumberFormatException ignore) {
                }

                material = split[0];
            }

            RecipeItem recipeItem = new RecipeItem(slot, amount, material, data);
            result.put(slot, recipeItem);
        }

        return result;
    }
}
