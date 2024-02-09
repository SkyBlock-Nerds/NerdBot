package net.hypixel.nerdbot.generator.parser;

import lombok.Getter;
import lombok.Setter;
import net.hypixel.nerdbot.generator.util.GeneratorStrings;

import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.util.HashMap;

@Getter
public class RecipeParser {
    private static final String FIELD_SEPARATOR = ",";
    private final HashMap<Integer, RecipeItem> recipeData;
    private boolean successfullyParsed = false;
    private String errorString;

    public RecipeParser() {
        recipeData = new HashMap<>();
    }

    /**
     * Strips the string of any links or malicious data
     *
     * @param item the recipe item to clean
     *
     * @return a sanitized string
     */
    private static String stripRecipeItem(String item) {
        return item.replaceAll("[^A-Za-z0-9,%#]", "");
    }

    /**
     * Attempts to convert the string into a number
     *
     * @param value the string to parse into an integer
     *
     * @return the number value of the string (null if it isn't a number)
     */
    @Nullable
    private static Integer tryParseInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public void parseRecipe(String recipe) {
        String[] recipeItems = recipe.split("%%");
        for (String item : recipeItems) {
            // checking that the string has a first item separator
            int firstSeparator = item.indexOf(FIELD_SEPARATOR);
            if (firstSeparator == -1 || firstSeparator + 1 >= item.length()) {
                errorString = String.format(GeneratorStrings.MISSING_FIELD_SEPARATOR, stripRecipeItem(item));
                return;
            }

            // checking that there is a second separator
            int secondSeparator = item.indexOf(FIELD_SEPARATOR, firstSeparator + 1);
            if (secondSeparator == -1 || secondSeparator + 1 >= item.length()) {
                errorString = String.format(GeneratorStrings.MISSING_FIELD_SEPARATOR, stripRecipeItem(item));
                return;
            }

            // checking if there is a third item for extra data
            int thirdSeparator = item.indexOf(FIELD_SEPARATOR, secondSeparator + 1);
            String itemName;
            String extraContent = "";
            if (thirdSeparator != -1) {
                itemName = item.substring(secondSeparator + 1, thirdSeparator);
                extraContent = item.substring(thirdSeparator + 1);
            } else {
                itemName = item.substring(secondSeparator + 1);
            }

            // checking that the entered slot is a number and hasn't already been assigned
            String slotString = item.substring(0, firstSeparator);
            Integer slot = tryParseInteger(slotString.strip());
            if (slot == null) {
                errorString = String.format(GeneratorStrings.RECIPE_SLOT_NOT_INTEGER, stripRecipeItem(slotString), stripRecipeItem(item));
                return;
            } else if (recipeData.containsKey(slot)) {
                errorString = String.format(GeneratorStrings.RECIPE_SLOT_DUPLICATED, slot, stripRecipeItem(item));
                return;
            }

            // checking that the entered item amount is a number and is in the range if 1-64
            String amountString = item.substring(firstSeparator + 1, secondSeparator);
            Integer amount = tryParseInteger(amountString.strip());
            if (amount == null) {
                errorString = String.format(GeneratorStrings.RECIPE_AMOUNT_NOT_INTEGER, stripRecipeItem(amountString), stripRecipeItem(item));
                return;
            } else if (amount > 64 || amount < 1) {
                errorString = String.format(GeneratorStrings.RECIPE_AMOUNT_NOT_IN_RANGE, stripRecipeItem(amountString), stripRecipeItem(item));
                return;
            }

            // creating a new recipe item and adding it to the hash map
            RecipeItem recipeItem = new RecipeItem(slot, amount, itemName, extraContent);
            recipeData.put(slot, recipeItem);
        }

        // checking that the user has a recipe entered in
        if (recipeData.isEmpty()) {
            errorString = GeneratorStrings.MISSING_PARSED_RECIPE;
            return;
        }

        this.successfullyParsed = true;
    }

    @Getter
    public static class RecipeItem {
        private final int slot;
        private final int amount;
        private final String itemName;
        private final String extraDetails;
        @Setter
        private BufferedImage image;

        public RecipeItem(int slot, int amount, String itemName, String extraDetails) {
            this.slot = slot;
            this.amount = amount;
            this.itemName = itemName;
            this.extraDetails = extraDetails;
        }

    }
}
