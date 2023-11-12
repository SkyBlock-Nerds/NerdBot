package net.hypixel.nerdbot.util.watcher.rss.xmlparsers;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class SkyblockThreadHandler extends DefaultHandler {

    private static boolean finishedReadingPrelude = false;
    @Getter
    private SkyblockThreadParser.SkyblockForum skyblockForum;
    private StringBuilder elementValue;
    private static final String DESCRIPTION = "description";
    private static final String ATOMLINK = "atom:link";
    private static final String CHANNEL = "channel";
    private static final String ITEM = "item";
    private static final String TITLE = "title";
    private static final String PUBDATE = "pubDate";
    private static final String LINK = "link";
    private static final String GUID = "guid";
    private static final String CREATOR = "dc:creator";

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        // We first handle the prelude tags.
        if (!finishedReadingPrelude) {
            switch (qName) {
                case CHANNEL:
                    skyblockForum.setThreadlist(new ArrayList<>());
                    break;
                case TITLE, DESCRIPTION:
                    elementValue = new StringBuilder();
                    break;
                case ATOMLINK:
                    // This tag should mark the end of prelude, and thus we stop checking for these here.
                    finishedReadingPrelude = true;
                    break;
            }
            return;
        }

        // Now that we've completed every starter tag, we can move onto the actual threads.
        switch (qName) {
            case ITEM:
                skyblockForum.getThreadlist().add(new SkyblockThreadParser.HypixelThread());
                break;
            case TITLE, PUBDATE, LINK, GUID, CREATOR:
                elementValue = new StringBuilder();
                break;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        // We first read the prelude tags, once complete we can move onto the rest of the tags.
        if (!finishedReadingPrelude) {
            switch (qName) {
                case TITLE:
                    log.info("Reading RSS Feed: " + elementValue);
                    break;
                case DESCRIPTION:
                    log.info("Description: " + elementValue);
                    break;
            }
            return;
        }

        switch (qName) {
            case TITLE:
                latestThread().setTitle(elementValue.toString());
                break;
            case PUBDATE:
                latestThread().setPublicationDate(elementValue.toString());
                break;
            case LINK:
                latestThread().setLink(elementValue.toString());
                break;
            case GUID:
                latestThread().setGuid(elementValue.toString());
                break;
            case CREATOR:
                latestThread().setCreator(elementValue.toString());
                break;
        }
    }

    private SkyblockThreadParser.HypixelThread latestThread() {
        List<SkyblockThreadParser.HypixelThread> articleList = skyblockForum.getThreadlist();
        int latestArticleIndex = articleList.size() - 1;
        return articleList.get(latestArticleIndex);
    }


    @Override
    public void characters(char[] ch, int start, int length) {
        if (elementValue == null) {
            elementValue = new StringBuilder();
        } else {
            elementValue.append(ch, start, length);
        }
    }


    @Override
    public void startDocument() {
        skyblockForum = new SkyblockThreadParser.SkyblockForum();
    }
}

