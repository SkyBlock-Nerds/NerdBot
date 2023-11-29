package net.hypixel.nerdbot.api.urlwatcher;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.util.JsonUtil;
import net.hypixel.nerdbot.util.Tuple;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

@Log4j2
public class URLWatcher {

    @Getter
    private final String url;
    @Getter
    @Setter
    private String lastContent;
    private final Timer timer;
    @Getter
    private boolean active;
    private final OkHttpClient client;
    private final Map<String, String> headers;

    public URLWatcher(String url) {
        this(url, null);
    }

    public URLWatcher(String url, Map<String, String> headers) {
        this.client = new OkHttpClient();
        this.url = url;
        this.headers = headers;
        this.timer = new Timer();
        this.lastContent = fetchContent();
    }

    public void startWatching(long interval, TimeUnit unit, DataHandler handler) {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                String newContent = fetchContent();
                if (newContent != null && !newContent.equals(lastContent)) {
                    List<Tuple<String, Object, Object>> changedValues = JsonUtil.findChangedValues(JsonUtil.parseStringToMap(lastContent), JsonUtil.parseStringToMap(newContent), "");
                    handler.handleData(lastContent, newContent, changedValues);
                    lastContent = newContent;
                    log.debug("Watched " + url + " and found changes!\nOld content: " + lastContent + "\nNew content: " + newContent);
                }
            }
        }, 0, unit.toMillis(interval));

        log.info("Started watching " + url);
        active = true;
    }

    public void simulateDataChange(String oldData, String newData, DataHandler handler) {
        List<Tuple<String, Object, Object>> changedValues = JsonUtil.findChangedValues(JsonUtil.parseStringToMap(oldData), JsonUtil.parseStringToMap(newData), "");
        handler.handleData(oldData, newData, changedValues);
        lastContent = newData;
        log.debug("Watched " + url + " and found changes!\nOld content: " + lastContent + "\nNew content: " + newData);
    }

    public void watchOnce(DataHandler handler) {
        String newContent = fetchContent();
        active = true;

        if (newContent != null && !newContent.equals(lastContent)) {
            handler.handleData(lastContent, newContent, JsonUtil.findChangedValues(JsonUtil.parseStringToMap(lastContent), JsonUtil.parseStringToMap(newContent), ""));
            lastContent = newContent;
            log.debug("Watched " + url + " once, found changes!\nOld content: " + lastContent + "\nNew content: " + newContent);
        }

        active = false;
    }

    public void stopWatching() {
        timer.cancel();
        log.info("Stopped watching " + url);
        active = false;
    }

    public String fetchContent() {
        log.debug("Fetching content from " + url);

        Request.Builder requestBuilder = new Request.Builder().url(url);

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }
        }

        Request request = requestBuilder.build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String content = response.body().string();
                log.debug("Successfully fetched content from " + url + "!" + " (Content: " + content + ")");
                return content;
            } else {
                log.error("Failed to fetch content from " + url + "! (Response: " + response + ")");
            }
        } catch (IOException e) {
            log.error("Failed to fetch content from " + url + "!" + " (Exception: " + e.getMessage() + ")");
            e.printStackTrace();
        }

        return null;
    }

    public interface DataHandler {
        void handleData(String oldContent, String newContent, List<Tuple<String, Object, Object>> changedValues);
    }
}
