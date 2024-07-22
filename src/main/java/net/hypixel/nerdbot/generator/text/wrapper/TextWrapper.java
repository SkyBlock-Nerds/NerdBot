package net.hypixel.nerdbot.generator.text.wrapper;

import net.hypixel.nerdbot.generator.text.segment.LineSegment;

import java.util.List;

public class TextWrapper {

    public List<List<LineSegment>> wrapSegment(String text, int maxLineLength) {
        LineWrapper lineWrapper = new LineWrapper(maxLineLength);
        return lineWrapper.wrapText(text);
    }
}
