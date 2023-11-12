package net.hypixel.nerdbot.util.watcher.rss;

import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.util.Tuple;
import net.hypixel.nerdbot.util.watcher.URLWatcher;

import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static net.hypixel.nerdbot.util.watcher.rss.SkyblockUpdateDataHandler.handleThread;
import static net.hypixel.nerdbot.util.watcher.rss.xmlparsers.SkyblockThreadParser.getLastPostedSkyblockThread;

@Log4j2
public class HypixelThreadURLWatcher extends URLWatcher {

    public HypixelThreadURLWatcher(String url) {
        super(url);
    }

    public HypixelThreadURLWatcher(String url, Map<String, String> headers) {
        super(url, headers);
    }

    @Override
    public void startWatching(long interval, TimeUnit unit, URLWatcher.DataHandler handler) {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                String newContent = fetchContent();
                if (newContent != null && !newContent.equals(lastContent)) {
                    handleThread(getLastPostedSkyblockThread(newContent));
                    lastContent = newContent;
                    log.debug("Watched " + url + " and found changes!\nOld content: " + lastContent + "\nNew content: " + newContent);
                }
            }
        }, 0, unit.toMillis(interval));

        log.info("Started watching " + url);
        active = true;
    }

    @Override
    public void watchOnce(URLWatcher.DataHandler handler) {
        String newContent = fetchContent();
        active = true;

        if (newContent != null && !newContent.equals(getLastContent())) {
            handleThread(getLastPostedSkyblockThread(newContent));
            lastContent = newContent;
            log.debug("Watched " + url + " once, found changes!\nOld content: " + getLastContent() + "\nNew content: " + newContent);
        }

        active = false;
    }

    public interface DataHandler {
        void handleData(String oldContent, String newContent, List<Tuple<String, Object, Object>> changedValues);
    }
}
