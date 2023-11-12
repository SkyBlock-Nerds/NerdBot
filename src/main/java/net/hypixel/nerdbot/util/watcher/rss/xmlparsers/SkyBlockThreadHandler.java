package net.hypixel.nerdbot.util.watcher.rss.xmlparsers;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;

@Log4j2
public class SkyBlockThreadHandler extends DefaultHandler {

    private static boolean finishedReadingPrelude = false;
    @Getter
    private SkyBlockThreadParser.SkyBlockForum skyBlockForum;
    private StringBuilder elementValue;
    private static String forum = null;
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
                    skyBlockForum.setThreadList(new ArrayList<>());
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
                skyBlockForum.getThreadList().add(new SkyBlockThreadParser.HypixelThread());
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
                    forum = elementValue.toString();
                    break;
                case DESCRIPTION:
                    // For future use if someone wants to know the description of this forum.
                    break;
            }
            return;
        }

        switch (qName) {
            case TITLE:
                latestThread().setTitle(elementValue.toString());
                latestThread().setForum(forum);
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

    private SkyBlockThreadParser.HypixelThread latestThread() {
        List<SkyBlockThreadParser.HypixelThread> threadList = skyBlockForum.getThreadList();
        int latestArticleIndex = threadList.size() - 1;
        return threadList.get(latestArticleIndex);
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
        skyBlockForum = new SkyBlockThreadParser.SkyBlockForum();
    }
}

