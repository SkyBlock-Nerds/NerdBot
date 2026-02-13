package net.hypixel.nerdbot.core;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Nullable;

@UtilityClass
public class EnumUtils {

    @Nullable
    public static <T extends Enum<T>> T findValue(T[] enumSet, String match) {
        for (T enumItem : enumSet) {
            if (match.equalsIgnoreCase(enumItem.name())) {
                return enumItem;
            }
        }

        return null;
    }
}