package net.hypixel.nerdbot.generator.text.segment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.text.ChatFormat;
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

    /**
     * Creates a {@link List<>} of {@link LineSegment}s from a {@link String}.
     *
     * @param legacyText            The text that will be parsed into a {@link List<>} of {@link LineSegment}s.
     * @param symbolSubstitute      The symbol that will indicate when a formatting code is about to be used.
     * @param preserveFormatting    Determines if the formatting from the previous {@link LineSegment} will be carried over.
     *
     * @return A {@link List<>} of {@link LineSegment}s where every "\n" indicates a new {@link LineSegment}
     */
    public static @NotNull List<LineSegment> fromLegacy(@NotNull String legacyText, char symbolSubstitute, boolean preserveFormatting) {
        if (preserveFormatting) {
            List<String> lines = Util.safeArrayStream(legacyText.split("(\n|\\\\n)")).toList();
            List<LineSegment> lineSegments = new ArrayList<>();

            for (String line : lines) {
                if (!lineSegments.isEmpty()){
                    StringBuilder previousLineFormatting = new StringBuilder();

                    LineSegment lastLineSegment = lineSegments.get(lineSegments.size() - 1);
                    ColorSegment lastColorSegment = lastLineSegment.segments.get(lastLineSegment.segments.size() - 1);

                    previousLineFormatting.append(symbolSubstitute).append(lastColorSegment.color.getCode());

                    if (lastColorSegment.isObfuscated()) {
                        previousLineFormatting.append(symbolSubstitute).append(ChatFormat.OBFUSCATED.getCode());
                    }
                    if (lastColorSegment.isBold()) {
                        previousLineFormatting.append(symbolSubstitute).append(ChatFormat.BOLD.getCode());
                    }
                    if (lastColorSegment.isStrikethrough()){
                        previousLineFormatting.append(symbolSubstitute).append(ChatFormat.STRIKETHROUGH.getCode());
                    }
                    if (lastColorSegment.isUnderlined()){
                        previousLineFormatting.append(symbolSubstitute).append(ChatFormat.UNDERLINE.getCode());
                    }
                    if (lastColorSegment.isItalic()) {
                        previousLineFormatting.append(symbolSubstitute).append(ChatFormat.ITALIC.getCode());
                    }

                    lineSegments.add(TextSegment.fromLegacy(previousLineFormatting + line, symbolSubstitute));
                }
                else {
                    lineSegments.add(TextSegment.fromLegacy(line, symbolSubstitute));
                }
            }

            return lineSegments;
        }
        else {
            return Util.safeArrayStream(legacyText.split("(\n|\\\\n)"))
                    .map(line -> TextSegment.fromLegacy(line, symbolSubstitute))
                    .toList();
        }
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