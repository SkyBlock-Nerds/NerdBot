package net.hypixel.nerdbot.core;

import java.util.Arrays;
import java.util.stream.Stream;

public class ArrayUtils {

    private ArrayUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static Stream<String> safeArrayStream(String[]... arrays) {
        Stream<String> stream = Stream.empty();

        if (arrays != null) {
            for (String[] array : arrays) {
                stream = Stream.concat(stream, (array == null) ? Stream.empty() : Arrays.stream(array));
            }
        }

        return stream;
    }

    public static Stream<Object> safeArrayStream(Object[]... arrays) {
        Stream<Object> stream = Stream.empty();

        if (arrays != null) {
            for (Object[] array : arrays) {
                stream = Stream.concat(stream, (array == null) ? Stream.empty() : Arrays.stream(array));
            }
        }

        return stream;
    }
}