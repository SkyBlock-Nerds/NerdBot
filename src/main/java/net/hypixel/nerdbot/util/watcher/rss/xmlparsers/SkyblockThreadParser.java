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
public class SkyblockThreadParser {

    public static HypixelThread getLastPostedSkyblockThread(String XML) {
        try {
            return parseSkyblockThreads(XML).get(0);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<HypixelThread> parseSkyblockThreads(String XML) throws ParserConfigurationException, SAXException, IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();

        SkyblockThreadHandler skyblockThreadHandler = new SkyblockThreadHandler();
        InputStream targetStream = new ByteArrayInputStream(XML.getBytes());
        saxParser.parse(targetStream, skyblockThreadHandler);
        return skyblockThreadHandler.getSkyblockForum().getThreadlist();
    }


    @Getter
    @Setter
    public static class SkyblockForum {
        private List<HypixelThread> threadlist;
    }

    @Getter
    @Setter
    public static class HypixelThread {
        private String title;
        private String publicationDate;
        private String link;
        private String guid;
        private String creator;
    }
}
