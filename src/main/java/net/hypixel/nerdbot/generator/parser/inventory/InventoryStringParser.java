package net.hypixel.nerdbot.generator.parser.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.item.InventoryItem;
import net.hypixel.nerdbot.generator.parser.Parser;
import net.hypixel.nerdbot.util.Range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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

        ArrayList<Integer> amounts = new ArrayList<>();
        ArrayList<Integer> slots = new ArrayList<>();

        for (String pair : keyValuePairs) {
            String[] targetSlot = pair.split(":");

            if (targetSlot.length != 2) {
                throw new GeneratorException("Invalid slot or amount format: `%s` in slot data: `%s` for material: `%s,%s`", pair.trim(), slotMap, material, data);
            }

            int amount = Range.between(1, 64).fit(Integer.parseInt(targetSlot[1].trim()));
            int[] parsedSlots = parseSlotRange(targetSlot[0].trim(), this.totalSlots);

            for (int slot : parsedSlots) {
                slots.add(slot);
                amounts.add(amount);
            }
        }

        int[] slotArray = slots.stream().mapToInt(i -> i).toArray();
        int[] amountArray = amounts.stream().mapToInt(i -> i).toArray();

        return new InventoryItem(slotArray, amountArray, material, data);
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

        int[] slotArrayResult = parseSlotRange(slotData, this.totalSlots);
        int[] amountArray = new int[slotArrayResult.length];
        Arrays.fill(amountArray, amount);

        return new InventoryItem(slotArrayResult, amountArray, material, data);
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
                amount = Range.between(1, 64).fit(Integer.parseInt(slotData.substring(splitIndex + 1)));
            } catch (NumberFormatException ignored) {
            }

            slotData = slotData.substring(0, splitIndex);
        }

        int slot;

        try {
            slot = Range.between(1, this.totalSlots).fit(Integer.parseInt(slotData));
        } catch (NumberFormatException exception) {
            throw new GeneratorException("Invalid slot or amount: `%s` for material: `%s,%s`", slotAmount, material, data);
        }

        return new InventoryItem(slot, amount, material, data);
    }

    /**
     * Parses a string of slots into an array of integers.
     *
     * @param slotData   The string of slots to parse.
     * @param totalSlots The total number of slots in the inventory.
     *
     * @return An array of integers representing the slots.
     */
    private int[] parseSlotRange(String slotData, int totalSlots) {
        return Arrays.stream(slotData.split(","))
            .flatMap(value -> {
                value = value.trim();
                if (value.contains("-")) {
                    // Parse a range of slots
                    String[] rangeParts = value.split("-");
                    if (rangeParts.length != 2) {
                        throw new GeneratorException("Invalid range format: `%s`", value);
                    }

                    int startSlot = Range.between(1, totalSlots).fit(Integer.parseInt(rangeParts[0].trim()));
                    int endSlot = Range.between(1, totalSlots).fit(Integer.parseInt(rangeParts[1].trim()));

                    if (startSlot > endSlot) {
                        throw new GeneratorException("Start slot cannot be greater than end slot in range: `%s`", value);
                    }

                    return IntStream.rangeClosed(startSlot, endSlot).boxed();
                } else {
                    // Parse a single slot
                    try {
                        int slot = Range.between(1, totalSlots).fit(Integer.parseInt(value));
                        return Stream.of(slot);
                    } catch (NumberFormatException exception) {
                        throw new GeneratorException("Invalid slot: `%s`", value);
                    }
                }
            })
            .mapToInt(Integer::intValue)
            .toArray();
    }
}
