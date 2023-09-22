package net.hypixel.nerdbot.util;

import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Log4j2
public class URLWatcher {

    private final String url;
    private String lastContent;
    private final Timer timer;
    private final OkHttpClient client;
    private final Map<String, String> headers;

    public URLWatcher(String url) {
        this(url, new HashMap<>());
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
                    handler.handleData(lastContent, newContent, JsonUtil.findChangedValues(lastContent, newContent));
                    lastContent = newContent;
                }
            }
        }, 0, unit.toMillis(interval));

        log.info("Started watching " + url);
    }

    public void stopWatching() {
        timer.cancel();
        log.info("Stopped watching " + url);
    }

    private String fetchContent() {
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
                return response.body().string();
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
