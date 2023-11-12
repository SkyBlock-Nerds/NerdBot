package net.hypixel.nerdbot.util.watcher.rss.xmlparsers;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Log4j2
public class SkyBlockThreadParser {

    public static HypixelThread getLastPostedSkyBlockThread(String xml) {
        try {
            return parseSkyBlockThreads(xml).get(0);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<HypixelThread> parseSkyBlockThreads(String xml) throws ParserConfigurationException, SAXException, IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();

        SkyBlockThreadHandler skyblockThreadHandler = new SkyBlockThreadHandler();
        InputStream targetStream = new ByteArrayInputStream(xml.getBytes());
        saxParser.parse(targetStream, skyblockThreadHandler);
        return skyblockThreadHandler.getSkyBlockForum().getThreadList();
    }

    @Getter
    @Setter
    public static class SkyBlockForum {
        private List<HypixelThread> threadList;
    }

    @Getter
    @Setter
    public static class HypixelThread {
        private String title;
        private String publicationDate;
        private String link;
        private String guid;
        private String creator;
        private String forum;
    }
}
