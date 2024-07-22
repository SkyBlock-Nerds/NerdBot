package net.hypixel.nerdbot.generator.text.wrapper;

import net.hypixel.nerdbot.generator.text.segment.LineSegment;

import java.util.ArrayList;
import java.util.List;

public class LineWrapper {
    private final int maxLineLength;
    private final List<List<LineSegment>> lines;
    private final StringBuilder currentLine;
    private String lastColorCode;
    private String lastFormattingCodes;

    public LineWrapper(int maxLineLength) {
        this.maxLineLength = maxLineLength;
        this.lines = new ArrayList<>();
        this.currentLine = new StringBuilder();
        this.lastColorCode = "";
        this.lastFormattingCodes = "";
    }

    public List<List<LineSegment>> wrapText(String text) {
        String[] words = text.split("\\s+");

        for (String word : words) {
            addWord(word);
        }

        addRemainingLine();
        handleNoSpaces(text);
        return lines;
    }

    private void addWord(String word) {
        if (currentLine.length() + word.length() + 1 > maxLineLength) {
            addCurrentLineToLines();
        }

        if (!currentLine.isEmpty()) {
            currentLine.append(" ");
        }

        currentLine.append(word);
    }

    private void addCurrentLineToLines() {
        String lineToAdd = currentLine.toString().trim();

        if (!lineToAdd.startsWith("&")) {
            lineToAdd = lastColorCode + lastFormattingCodes + lineToAdd;
        }

        lines.add(LineSegment.fromLegacy(lineToAdd, '&'));
        updateFormattingCodes(lineToAdd);
        currentLine.setLength(0);
    }

    private void addRemainingLine() {
        if (!currentLine.isEmpty()) {
            addCurrentLineToLines();
        }
    }

    private void handleNoSpaces(String text) {
        if (currentLine.isEmpty() && !text.contains(" ")) {
            for (int i = 0; i < text.length(); i += maxLineLength) {
                String part = text.substring(i, Math.min(i + maxLineLength, text.length()));

                if (!part.startsWith("&")) {
                    part = lastColorCode + lastFormattingCodes + part;
                }

                lines.add(LineSegment.fromLegacy(part, '&'));
                updateFormattingCodes(part);
            }
        }
    }

    private void updateFormattingCodes(String line) {
        lastColorCode = getLastColorCode(line);
        lastFormattingCodes = getLastFormattingCodes(line);
    }

    private String getLastColorCode(String text) {
        String colorCode = "";
        for (int i = text.length() - 2; i >= 0; i--) {
            if (text.charAt(i) == '&' && (i + 1 < text.length())) {
                char code = text.charAt(i + 1);

                if (Character.isLetterOrDigit(code)) {
                    colorCode = text.substring(i, i + 2);

                    if ("0123456789abcdef".indexOf(code) != -1) {
                        break;
                    }
                }
            }
        }
        return colorCode;
    }

    private String getLastFormattingCodes(String text) {
        StringBuilder formattingCodes = new StringBuilder();
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '&' && (i + 1 < text.length())) {
                char code = text.charAt(i + 1);

                if ("klmnor".indexOf(code) != -1) {
                    formattingCodes.append(text, i, i + 2);
                }
            }
        }
        return formattingCodes.toString();
    }
}
