package net.hypixel.nerdbot.generator.parser.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.item.InventoryItem;
import net.hypixel.nerdbot.generator.parser.Parser;
import net.hypixel.nerdbot.util.Range;

import java.util.ArrayList;

@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
public class InventoryStringParser implements Parser<ArrayList<InventoryItem>> {

    private final int totalSlots;

    /**
     * Parses a string into a map of recipe items.
     * <br>
     * NOTE: Slots are 1-indexed.
     *
     * @param input The string to parse.
     *
     * @return An array of {@link InventoryItem} objects to their respective slots.
     */
    @Override
    public ArrayList<InventoryItem> parse(String input) {
        String[] split = input.split("%%");
        ArrayList<InventoryItem> result = new ArrayList<>();

        for (String item : split) {
            item = item.trim();
            String[] components = item.split(":", 2);

            if (components.length != 2) {
                throw new GeneratorException("Unknown inventory item: `%s`", item);
            }

            // getting the material data
            String material = components[0];
            String data = null;
            if (material.contains(",")) {
                String[] dataSplit = material.split(",", 2);
                material = dataSplit[0];
                if (dataSplit.length > 1) {
                    data = dataSplit[1];
                }
            }

            String slotData = components[1];
            if (slotData.contains("{")) {
                result.add(itemFromMap(slotData, material, data));
            } else if (components[1].contains("[")) {
                result.add(itemFromArray(slotData, material, data));
            } else {
                result.add(itemFromAmount(slotData, material, data));
            }
        }

        return result;
    }

    /**
     * Converts the key-value pairs into slot and amount data for a {@link InventoryItem}
     * <br>
     *
     * @param slotMap  String-Dictionary with mapped slot index (key) and amount (value)
     * @param material Material of the item required
     * @param data     Extra information to alter the appearance of the material
     *
     * @return A mapped {@link InventoryItem}
     */
    private InventoryItem itemFromMap(String slotMap, String material, String data) {
        int endingBracket = slotMap.indexOf("}");
        String slotData = slotMap.substring(1, endingBracket != -1 ? endingBracket : slotMap.length() - 1);
        String[] keyValuePairs = slotData.split(",");
        int[] slots = new int[keyValuePairs.length];
        int[] amounts = new int[keyValuePairs.length];
        int index = 0;
        for (String pair : keyValuePairs) {
            String[] targetSlot = pair.split(":");
            try {
                slots[index] = Range.between(1, this.totalSlots).fit(Integer.parseInt(targetSlot[0].trim()));
                amounts[index] = Range.between(1, 64).fit(Integer.parseInt(targetSlot[1].trim()));
                index++;
            } catch (NumberFormatException exception) {
                throw new GeneratorException("Invalid slot or amount: `%s` in slot data: `%s` for material: `%s,%s`", pair.trim(), slotMap, material, data);
            }
        }
        return new InventoryItem(slots, amounts, material, data);
    }

    /**
     * Converts the array of slots into data for a {@link InventoryItem}
     * <br>
     *
     * @param slotArray String-Array with slots with optional amount outside the array
     * @param material  Material of the item required
     * @param data      Extra information to alter the appearance of the material
     *
     * @return A mapped {@link InventoryItem}
     */
    private InventoryItem itemFromArray(String slotArray, String material, String data) {
        String slotData = slotArray;

        int endingBracket = slotData.indexOf("]");
        int amount = 1;
        if (endingBracket != -1) {
            try {
                amount = Range.between(1, 64).fit(Integer.parseInt(slotData.substring(endingBracket).replaceAll("[^0-9]", "")));
            } catch (NumberFormatException ignored) {
            }
            slotData = slotData.substring(0, endingBracket + 1);
        }

        slotData = slotData.substring(1, slotData.length() - 1);
        String[] values = slotData.split(",");
        int[] slots = new int[values.length];
        int[] amounts = new int[values.length];
        int index = 0;
        for (String slotIndex : values) {
            try {
                slots[index] = Integer.parseInt(slotIndex.trim());
                amounts[index] = amount;
                index++;
            } catch (NumberFormatException exception) {
                throw new GeneratorException("Invalid slot: `%s` in slot data: `%s` for material: `%s,%s`", slotIndex.trim(), slotArray, material, data);
            }
        }
        return new InventoryItem(slots, amounts, material, data);
    }

    /**
     * Converts slot and amount into a {@link InventoryItem}
     * <br>
     *
     * @param slotAmount String with selected slot with optional amount after comma
     * @param material   Material of the item required
     * @param data       Extra information to alter the appearance of the material
     *
     * @return A mapped {@link InventoryItem}
     */
    private InventoryItem itemFromAmount(String slotAmount, String material, String data) {
        int amount = 1;

        String slotData = slotAmount;
        if (slotData.contains(",")) {
            int splitIndex = slotData.indexOf(",");
            try {
                amount = Range.between(1, 64).fit(Integer.parseInt(slotData.substring(splitIndex)));
            } catch (NumberFormatException ignored) {
            }
            slotData = slotData.substring(0, splitIndex);
        }

        int slot;
        try {
            slot = Integer.parseInt(slotData);
        } catch (NumberFormatException exception) {
            throw new GeneratorException("Invalid slot or amount: `%s` for material: `%s,%s`", slotAmount, material, data);
        }

        return new InventoryItem(slot, amount, material, data);
    }
}
