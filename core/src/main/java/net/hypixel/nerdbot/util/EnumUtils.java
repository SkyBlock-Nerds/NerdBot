package net.hypixel.nerdbot.util;

import org.jetbrains.annotations.Nullable;

public class EnumUtils {

    private EnumUtils() {
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