package net.hypixel.nerdbot.generator.text.wrapper;

import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.text.segment.LineSegment;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Log4j2
public class TextWrapper {

    public static List<List<LineSegment>> wrapSegment(String text, int maxLineLength) {
        return new LineWrapper(maxLineLength).wrapText(text);
    }

    public static List<List<LineSegment>> splitLines(List<String> lines, int maxLineLength) {
        List<List<LineSegment>> output = new CopyOnWriteArrayList<>();

        for (String line : lines) {
            log.debug("Processing line: {}", line);

            // adds blank line if the line is empty
            // since this seems to only trigger when using two newline characters in a row
            if (line == null || line.isBlank()) {
                log.debug("Adding blank line to output because line is blank or null");
                output.add(LineSegment.fromLegacy(" ", '&'));
                continue;
            }

            // split text into segments based on newline characters
            String[] segments = line.split("\n");

            for (String segment : segments) {
                log.debug("Processing segment: {}", segment);
                output.addAll(wrapSegment(segment, maxLineLength));
                log.debug("Added segment to output: {}", output);
            }
        }

        // throw an exception if every line is empty
        if (output.stream().allMatch(List::isEmpty)) {
            log.debug("All lines are empty! Not continuing with text wrapper");
            throw new GeneratorException("You cannot generate an empty tooltip!");
        }

        return output;
    }
}
