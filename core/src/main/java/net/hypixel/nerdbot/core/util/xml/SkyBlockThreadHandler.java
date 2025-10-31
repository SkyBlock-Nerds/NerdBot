package net.hypixel.nerdbot.core.util.xml;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class SkyBlockThreadHandler extends DefaultHandler {

    private static final String DESCRIPTION = "description";
    private static final String ATOMLINK = "atom:link";
    private static final String CHANNEL = "channel";
    private static final String ITEM = "item";
    private static final String TITLE = "title";
    private static final String PUBDATE = "pubDate";
    private static final String LINK = "link";
    private static final String GUID = "guid";
    private static final String CREATOR = "dc:creator";
    private static String forum = null;
    @Getter
    private SkyBlockThreadParser.SkyBlockForum skyBlockForum;
    private StringBuilder elementValue;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        // We first handle the prelude tags.
        if (!skyBlockForum.isFinishedReadingPrelude()) {
            switch (qName) {
                case CHANNEL:
                    skyBlockForum.setThreadList(new ArrayList<>());
                    break;
                case TITLE:
                    elementValue = new StringBuilder();
                    break;
                case ATOMLINK:
                    // This tag should mark the end of prelude, and thus we stop checking for these here.
                    skyBlockForum.setFinishedReadingPrelude(true);
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
        if (!skyBlockForum.isFinishedReadingPrelude()) {
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
                int guid;
                guid = Integer.parseInt(elementValue.toString());
                latestThread().setGuid(guid);
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