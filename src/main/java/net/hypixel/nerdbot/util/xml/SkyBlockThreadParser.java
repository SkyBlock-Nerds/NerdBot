package net.hypixel.nerdbot.util.xml;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@Slf4j
public class SkyBlockThreadParser {

    private SkyBlockThreadParser() {
    }

    public static List<HypixelThread> parseSkyBlockThreads(String xml) {
        SAXParserFactory factory = SAXParserFactory.newInstance();

        try {
            SAXParser saxParser = factory.newSAXParser();
            SkyBlockThreadHandler skyblockThreadHandler = new SkyBlockThreadHandler();
            InputStream targetStream = new ByteArrayInputStream(xml.getBytes());
            saxParser.parse(targetStream, skyblockThreadHandler);
            return skyblockThreadHandler.getSkyBlockForum().getThreadList();
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            log.error("Failed to parse content from: " + xml, exception);
        }

        return Collections.emptyList();
    }

    @Getter
    @Setter
    public static class SkyBlockForum {
        private boolean finishedReadingPrelude = false;
        private List<HypixelThread> threadList;
    }

    @Getter
    @Setter
    public static class HypixelThread {
        private String title;
        private String publicationDate;
        private String link;
        private int guid;
        private String creator;
        private String forum;
    }
}
