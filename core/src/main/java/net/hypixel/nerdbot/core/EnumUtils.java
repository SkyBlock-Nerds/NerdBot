package net.hypixel.nerdbot.core;

import org.jetbrains.annotations.Nullable;

public class EnumUtils {

    private EnumUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    @Nullable
    public static Enum<?> findValue(Enum<?>[] enumSet, String match) {
        for (Enum<?> enumItem : enumSet) {
            if (match.equalsIgnoreCase(enumItem.name()))
                return enumItem;
        }

        return null;
    }
}