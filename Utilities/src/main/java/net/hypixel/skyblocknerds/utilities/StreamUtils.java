package net.hypixel.skyblocknerds.utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class StreamUtils {

    @SafeVarargs
    public static <T> Stream<T> combineStreams(Stream<T>... streams) {
        AtomicReference<List<T>> result = new AtomicReference<>(List.of());
        Arrays.stream(streams)
                .filter(Objects::nonNull)
                .forEach(stream -> result.updateAndGet(list -> {
                    List<T> newList = new ArrayList<>(list);
                    stream.forEach(newList::add);
                    return newList;
                }));
        return result.get().stream();
    }
}
