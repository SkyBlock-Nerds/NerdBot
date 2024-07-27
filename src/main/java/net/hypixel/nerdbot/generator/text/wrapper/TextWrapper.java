package net.hypixel.nerdbot.generator.text.wrapper;

import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.text.segment.LineSegment;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TextWrapper {

    public static List<List<LineSegment>> wrapSegment(String text, int maxLineLength) {
        LineWrapper lineWrapper = new LineWrapper(maxLineLength);
        return lineWrapper.wrapText(text);
    }

    public static List<List<LineSegment>> splitLines(List<String> lines, int maxLineLength) {
        List<List<LineSegment>> output = new CopyOnWriteArrayList<>();

        for (String line : lines) {
            // adds blank line if the line is empty
            // since this seems to only trigger when using two newline characters in a row
            if (line == null || line.isBlank()) {
                output.add(LineSegment.fromLegacy(" ", '&'));
                continue;
            }

            // split text into segments based on newline characters
            String[] segments = line.split("\n");

            for (String segment : segments) {
                output.addAll(wrapSegment(segment, maxLineLength));
            }
        }

        // throw an exception if every line is empty
        if (output.stream().allMatch(List::isEmpty)) {
            throw new GeneratorException("You cannot generate an empty tooltip!");
        }

        return output;
    }
}
