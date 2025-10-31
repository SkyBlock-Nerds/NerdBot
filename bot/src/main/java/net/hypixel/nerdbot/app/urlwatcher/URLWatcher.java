package net.hypixel.nerdbot.app.urlwatcher;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.core.JsonUtils;
import net.hypixel.nerdbot.core.Tuple;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class URLWatcher {

    @Getter
    private final String url;
    private final Timer timer;
    private final OkHttpClient client;
    private final Map<String, String> headers;
    private final ExecutorService executorService;
    @Getter
    @Setter
    private String lastContent;
    @Getter
    private boolean active;

    public URLWatcher(String url) {
        this(url, null);
    }

    public URLWatcher(String url, Map<String, String> headers) {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
        this.url = url;
        this.headers = headers;
        this.timer = new Timer();
        this.executorService = Executors.newCachedThreadPool();
        this.lastContent = fetchContent();
    }

    public void startWatching(long interval, TimeUnit unit, DataHandler handler) {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                fetchContentAsync()
                    .thenAccept(newContent -> {
                        if (newContent != null && !newContent.equals(lastContent)) {
                            log.debug("Watched " + url + " and found changes!\nOld content: " + lastContent + "\nNew content: " + newContent);
                            handler.handleData(lastContent, newContent, JsonUtils.findChangedValues(JsonUtils.parseStringToMap(lastContent), JsonUtils.parseStringToMap(newContent), ""));
                            lastContent = newContent;
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Error fetching content asynchronously from " + url, throwable);
                        return null;
                    });
            }
        }, 0, unit.toMillis(interval));

        log.info("Started watching " + url);
        active = true;
    }

    public void simulateDataChange(String oldData, String newData, DataHandler handler) {
        List<Tuple<String, Object, Object>> changedValues = JsonUtils.findChangedValues(JsonUtils.parseStringToMap(oldData), JsonUtils.parseStringToMap(newData), "");
        handler.handleData(oldData, newData, changedValues);
        lastContent = newData;
        log.debug("Watched " + url + " and found changes!\nOld content: " + lastContent + "\nNew content: " + newData);
    }

    public void watchOnce(DataHandler handler) {
        active = true;

        fetchContentAsync()
            .thenAccept(newContent -> {
                if (newContent != null && !newContent.equals(lastContent)) {
                    handler.handleData(lastContent, newContent, JsonUtils.findChangedValues(JsonUtils.parseStringToMap(lastContent), JsonUtils.parseStringToMap(newContent), ""));
                    lastContent = newContent;
                    log.debug("Watched " + url + " once, found changes!\nOld content: " + lastContent + "\nNew content: " + newContent);
                }
                active = false;
            })
            .exceptionally(throwable -> {
                log.error("Error fetching content asynchronously from " + url, throwable);
                active = false;
                return null;
            });
    }

    public void stopWatching() {
        timer.cancel();
        executorService.shutdown();
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
            if (response.isSuccessful()) {
                String content = response.body().string();
                log.debug("Successfully fetched content from " + url + "!" + " (Content: " + content + ")");
                return content;
            } else {
                log.error("Failed to fetch content from " + url + "! (Response: " + response + ")");
            }
        } catch (IOException exception) {
            log.error("Failed to fetch content from " + url + "!", exception);
        }

        return null;
    }

    public CompletableFuture<String> fetchContentAsync() {
        log.debug("Fetching content asynchronously from " + url);

        Request.Builder requestBuilder = new Request.Builder().url(url);

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }
        }

        Request request = requestBuilder.build();
        CompletableFuture<String> future = new CompletableFuture<>();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.error("Failed to fetch content asynchronously from " + url, e);
                future.complete(null);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (response) {
                    if (response.isSuccessful()) {
                        String content = response.body().string();
                        log.debug("Successfully fetched content asynchronously from " + url + "!" + " (Content: " + content + ")");
                        future.complete(content);
                    } else {
                        log.error("Failed to fetch content asynchronously from " + url + "! (Response: " + response + ")");
                        future.complete(null);
                    }
                } catch (IOException e) {
                    log.error("Error reading response body from " + url, e);
                    future.complete(null);
                }
            }
        });

        return future;
    }

    public interface DataHandler {
        void handleData(String oldContent, String newContent, List<Tuple<String, Object, Object>> changedValues);
    }
}