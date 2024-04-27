package net.hypixel.skyblocknerds.utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public class StreamUtils {

    @SafeVarargs
    public static <T> Stream<T> combineStreams(Stream<T>... streams) {
        List<Stream<T>> streamList = new ArrayList<>(Arrays.asList(streams));
        AtomicReference<Stream<T>> combinedStream = new AtomicReference<>(streamList.remove(0));
        streamList.forEach(stream -> combinedStream.set(Stream.concat(combinedStream.get(), stream)));
        return combinedStream.get();
    }
}
