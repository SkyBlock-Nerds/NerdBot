package net.hypixel.nerdbot.generator.text.segment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.util.Util;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public final class LineSegment {

    private final @NotNull List<ColorSegment> segments;

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static @NotNull List<LineSegment> fromLegacy(@NotNull String legacyText, char symbolSubstitute) {
        String[] lines = legacyText.split("(\n|\\\\n)");
        ArrayList<LineSegment> segments = new ArrayList<>();

        for (String line : lines){
            if (!segments.isEmpty()) {
                List<ColorSegment> colorSegments = segments.get(segments.size() - 1).getSegments();
                if (!colorSegments.isEmpty()) {
                    ColorSegment lastColorSegment = colorSegments.get(colorSegments.size() - 1);
                    segments.add(TextSegment.fromLegacy(line, symbolSubstitute, lastColorSegment.settings));
                    continue;
                }
            }

            segments.add(TextSegment.fromLegacy(line, symbolSubstitute));
        }

        return segments;
    }

    public int length() {
        return this.getSegments()
            .stream()
            .mapToInt(colorSegment -> colorSegment.getText().length())
            .sum();
    }

    public @NotNull JsonElement toJson() {
        JsonArray rootArray = new JsonArray();
        rootArray.add("");
        this.getSegments().forEach(segment -> rootArray.add(segment.toJson()));
        return rootArray;
    }

    public static class Builder implements ClassBuilder<LineSegment> {

        private final List<ColorSegment> segments = new CopyOnWriteArrayList<>();

        public Builder withSegments(@NotNull ColorSegment... segments) {
            return this.withSegments(Arrays.asList(segments));
        }

        public Builder withSegments(@NotNull Iterable<ColorSegment> segments) {
            segments.forEach(this.segments::add);
            return this;
        }

        @Override
        public @NotNull LineSegment build() {
            return new LineSegment(this.segments.stream().toList());
        }
    }
}