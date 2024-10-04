package net.hypixel.nerdbot.generator.text.wrapper;

import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.generator.parser.Parser;
import net.hypixel.nerdbot.generator.parser.text.ColorCodeParser;
import net.hypixel.nerdbot.generator.parser.text.GemstoneParser;
import net.hypixel.nerdbot.generator.parser.text.IconParser;
import net.hypixel.nerdbot.generator.parser.text.StatParser;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Log4j2
public class TextWrapper {

    public static List<String> wrapString(String string, int maxLineLength) {
        List<String> lines = new CopyOnWriteArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        int currentLength = 0;

        if (!string.contains(" ")) {
            String parsedLine = parseLine(string);
            String strippedString = stripColorCodes(parsedLine);

            for (int i = 0; i < strippedString.length(); i += maxLineLength) {
                lines.add(parsedLine.substring(i, Math.min(i + maxLineLength, parsedLine.length())));
            }

            return lines;
        }

        String[] splitLines = string.split("(\n|\\\\n)");

        for (String splitLine : splitLines) {
            String[] words = splitLine.split(" ");

            for (String word : words) {
                String parsedWord = parseLine(word);
                String strippedWord = stripColorCodes(parsedWord);

                if (currentLength + strippedWord.length() > maxLineLength) {
                    addLine(lines, currentLine);
                    currentLine = new StringBuilder();
                    currentLength = 0;
                }

                currentLine.append(parsedWord).append(" ");
                currentLength += strippedWord.length();
            }

            addLine(lines, currentLine);
            currentLine = new StringBuilder();
            currentLength = 0;
        }

        return lines;
    }

    private static String stripColorCodes(String string) {
        return string.replaceAll("[&§][0-9a-fA-FK-ORk-or]", "");
    }

    private static void addLine(List<String> lines, StringBuilder currentLine) {
        lines.add(currentLine.toString().trim());
    }

    private static String parseLine(String line) {
        return Parser.parseString(line, List.of(
            new ColorCodeParser(),
            new IconParser(),
            new StatParser(),
            new GemstoneParser()
        ));
    }
}
