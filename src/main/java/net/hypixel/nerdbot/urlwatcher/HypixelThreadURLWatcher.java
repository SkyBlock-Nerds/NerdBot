package net.hypixel.nerdbot.urlwatcher;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.urlwatcher.handler.SkyBlockUpdateDataHandler;
import net.hypixel.nerdbot.util.xml.SkyBlockThreadParser;
import net.hypixel.nerdbot.util.xml.SkyBlockThreadParser.HypixelThread;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.Call;
import okhttp3.Callback;
import org.jetbrains.annotations.NotNull;

@Log4j2
public class HypixelThreadURLWatcher {

    @Getter
    private final String url;
    private final Timer timer;
    private final OkHttpClient client;
    private final Map<String, String> headers;
    @Getter
    @Setter
    private int lastGuid;
    @Getter
    private boolean active;
    private final ExecutorService executorService;

    public HypixelThreadURLWatcher(String url) {
        this(url, null);
    }

    public HypixelThreadURLWatcher(String url, Map<String, String> headers) {
        this.client = new OkHttpClient();
        this.url = url;
        this.headers = headers;
        this.timer = new Timer();
        this.executorService = Executors.newCachedThreadPool();
        this.lastGuid = getHighestGuid();
    }

    public void startWatching(long interval, TimeUnit unit) {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                fetchAndParseContentAsync()
                    .thenAccept(hypixelThreads -> {
                        if (hypixelThreads == null) {
                            return;
                        }

                        for (HypixelThread thread : hypixelThreads) {
                            if (thread.getGuid() > lastGuid) {
                                log.debug("Watched " + url + " and found newest GUID!\nOld GUID: " + lastGuid + "\nNew GUID: " + thread.getGuid());
                                lastGuid = thread.getGuid();
                                SkyBlockUpdateDataHandler.handleThread(thread);
                            }
                        }
                    })
                    .exceptionally(throwable -> {
                        log.error("Error fetching and parsing content asynchronously from " + url, throwable);
                        return null;
                    });
            }
        }, 0, unit.toMillis(interval));

        log.info("Started watching " + url);
        active = true;
    }

    public void watchOnce() {
        active = true;
        
        fetchAndParseContentAsync()
            .thenAccept(hypixelThreads -> {
                if (hypixelThreads == null) {
                    active = false;
                    return;
                }

                for (HypixelThread thread : hypixelThreads) {
                    if (thread.getGuid() > lastGuid) {
                        log.debug("Watched " + url + " and found newest Guid!\nOld Guid: " + lastGuid + "\nNew Guid: " + thread.getGuid());
                        lastGuid = thread.getGuid();
                        SkyBlockUpdateDataHandler.handleThread(thread);
                    }
                }
                active = false;
            })
            .exceptionally(throwable -> {
                log.error("Error fetching and parsing content asynchronously from " + url, throwable);
                active = false;
                return null;
            });
    }

    public int getHighestGuid() {
        List<HypixelThread> hypixelThreads = fetchAndParseContent();
        if (hypixelThreads == null || hypixelThreads.isEmpty()) {
            return 0;
        }
        
        int[] guidList = new int[hypixelThreads.size()];

        for (int x = 0; x < hypixelThreads.size(); x++) {
            guidList[x] = hypixelThreads.get(x).getGuid();
        }

        return Arrays.stream(guidList).max().orElse(0);
    }

    public List<HypixelThread> fetchAndParseContent() {
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
                return SkyBlockThreadParser.parseSkyBlockThreads(content);
            } else {
                log.error("Failed to fetch content from " + url + "! (Response: " + response + ")");
            }
        } catch (IOException exception) {
            log.error("Failed to fetch content from " + url + "!", exception);
        }

        return null;
    }

    public CompletableFuture<List<HypixelThread>> fetchAndParseContentAsync() {
        log.debug("Fetching content asynchronously from " + url);

        Request.Builder requestBuilder = new Request.Builder().url(url);

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.header(entry.getKey(), entry.getValue());
            }
        }

        Request request = requestBuilder.build();
        CompletableFuture<List<HypixelThread>> future = new CompletableFuture<>();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                log.error("Failed to fetch content asynchronously from {}", url, e);
                future.complete(null);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (response) {
                    if (response.isSuccessful() && response.body() != null) {
                        String content = response.body().string();
                        log.debug("Successfully fetched content asynchronously from " + url + "!" + " (Content: " + content + ")");
                        List<HypixelThread> threads = SkyBlockThreadParser.parseSkyBlockThreads(content);
                        future.complete(threads);
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
}
