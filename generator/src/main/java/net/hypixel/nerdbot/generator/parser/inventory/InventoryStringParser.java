package net.hypixel.nerdbot.generator.parser.inventory;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.hypixel.nerdbot.core.Range;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.item.InventoryItem;
import net.hypixel.nerdbot.generator.parser.Parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.stream.Collectors;
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

        for (String rawItem : split) {
            String item = rawItem.trim();
            int slotSeparatorIndex = findSlotSeparatorIndex(item);

            if (slotSeparatorIndex == -1) {
                throw new GeneratorException("Incorrect amount of components present in item: `%s` (missing a valid slot separator `:`)".formatted(item));
            }

            // Split the material/modifier section from slot data
            String material = item.substring(0, slotSeparatorIndex).trim();
            String slotData = item.substring(slotSeparatorIndex + 1).trim();
            String data = null;
            Integer durability = null;

            if (material.contains(",")) {
                String[] dataSplit = material.split(",");
                material = dataSplit[0].trim();

                if (dataSplit.length > 1) {
                    List<String> modifiers = new ArrayList<>();
                    for (int i = 1; i < dataSplit.length; i++) {
                        String part = dataSplit[i].trim();
                        if (part.isEmpty()) {
                            continue;
                        }

                        boolean isPotentialDurability = i == dataSplit.length - 1 && isInteger(part);
                        if (isPotentialDurability) {
                            durability = Range.between(0, 100).fit(Integer.parseInt(part));
                        } else {
                            modifiers.add(part);
                        }
                    }

                    if (!modifiers.isEmpty()) {
                        data = String.join(",", modifiers);
                    }
                }
            }

            if (slotData.contains("{")) {
                result.add(itemFromMap(slotData, material, data, durability));
            } else if (slotData.contains("[")) {
                result.add(itemFromArray(slotData, material, data, durability));
            } else {
                result.add(itemFromAmount(slotData, material, data, durability));
            }
        }

        ListIterator<InventoryItem> iterator = result.listIterator();

        if (iterator.hasNext()) {
            do {
                InventoryItem item = iterator.next();

                if (!iterator.hasNext()) {
                    break;
                }

                InventoryItem nextItem = result.get(iterator.nextIndex());
                int[] slots = item.getSlot();
                int[] nextSlots = nextItem.getSlot();

                // Find overlapping slots between all items
                Set<Integer> overlappingSlots = Arrays.stream(slots)
                    .boxed()
                    .filter(slot -> Arrays.stream(nextSlots).anyMatch(nextSlot -> nextSlot == slot))
                    .collect(Collectors.toSet());

                if (!overlappingSlots.isEmpty()) {
                    // Remove overlapping slots from the current item
                    int[] newSlots = Arrays.stream(slots)
                        .filter(slot -> !overlappingSlots.contains(slot))
                        .toArray();

                    // Create a new amounts array with the new slot data
                    int[] newAmounts = Arrays.copyOfRange(item.getAmount(), 0, newSlots.length);

                    item.setSlot(newSlots);
                    item.setAmount(newAmounts);
                }
            } while (iterator.hasNext());
        }

        return result;
    }

    /**
     * Converts the key-value pairs into slot and amount data for a {@link InventoryItem}
     *
     * @param slotMap  String-Dictionary with mapped slot index (key) and amount (value)
     * @param material Material of the item required
     * @param data     Extra information to alter the appearance of the material
     *
     * @return A mapped {@link InventoryItem}
     */
    private InventoryItem itemFromMap(String slotMap, String material, String data, Integer durability) {
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

        return new InventoryItem(slotArray, amountArray, material, data, durability);
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
    private InventoryItem itemFromArray(String slotArray, String material, String data, Integer durability) {
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

        return new InventoryItem(slotArrayResult, amountArray, material, data, durability);
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
    private InventoryItem itemFromAmount(String slotAmount, String material, String data, Integer durability) {
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

        return new InventoryItem(slot, amount, material, data, durability);
    }

    private int findSlotSeparatorIndex(String item) {
        int braceDepth = 0;
        int bracketDepth = 0;

        for (int i = 0; i < item.length(); i++) {
            char current = item.charAt(i);

            switch (current) {
                case '{' -> braceDepth++;
                case '}' -> braceDepth = Math.max(0, braceDepth - 1);
                case '[' -> bracketDepth++;
                case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
                case ':' -> {
                    if (braceDepth > 0 || bracketDepth > 0) {
                        continue;
                    }

                    int j = i + 1;
                    while (j < item.length() && Character.isWhitespace(item.charAt(j))) {
                        j++;
                    }

                    if (j >= item.length()) {
                        return -1;
                    }

                    char next = item.charAt(j);
                    if (Character.isLetter(next)) {
                        // Likely part of a namespaced ID (e.g., minecraft:stone)
                        continue;
                    }

                    return i;
                }
                default -> {
                }
            }
        }

        return -1;
    }

    private boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
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
