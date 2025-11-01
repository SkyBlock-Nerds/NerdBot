package net.hypixel.nerdbot.app.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.prometheus.client.Summary;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import net.hypixel.nerdbot.app.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.core.HttpClient;
import net.hypixel.nerdbot.core.UUIDUtils;
import net.hypixel.nerdbot.core.exception.HttpException;
import net.hypixel.nerdbot.core.json.HypixelPlayerResponse;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.discord.storage.database.model.user.stats.MojangProfile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Discord-specific HTTP utilities with Prometheus metrics integration.
 * This class extends the core HttpClient functionality with monitoring and domain-specific methods.
 */
@Slf4j
public class HttpUtils {

    private HttpUtils() {
    }

    public static MojangProfile getMojangProfile(String username) throws HttpException {
        String mojangUrl = String.format("https://api.mojang.com/users/profiles/minecraft/%s", username);

        if (UUIDUtils.isUUID(username)) {
            mojangUrl = String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s", username);
        }

        try {
            String body = HttpClient.getString(mojangUrl);
            return BotEnvironment.GSON.fromJson(body, MojangProfile.class);
        } catch (IOException | InterruptedException exception) {
            throw new HttpException("Network error fetching profile for `" + username + "`", exception);
        } catch (Exception exception) {
            throw new HttpException("Failed to parse Mojang profile for `" + username + "`: " + exception.getMessage(), exception);
        }
    }

    public static CompletableFuture<MojangProfile> getMojangProfileAsync(String username) {
        String mojangUrl = String.format("https://api.mojang.com/users/profiles/minecraft/%s", username);

        if (UUIDUtils.isUUID(username)) {
            mojangUrl = String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s", username);
        }

        return HttpClient.getStringAsync(mojangUrl)
            .thenApply(body -> {
                try {
                    return BotEnvironment.GSON.fromJson(body, MojangProfile.class);
                } catch (JsonSyntaxException exception) {
                    throw new RuntimeException(new HttpException("Invalid JSON response from Mojang API for `" + username + "`", exception));
                } catch (IllegalStateException exception) {
                    throw new RuntimeException(new HttpException("Malformed Mojang profile data for `" + username + "`", exception));
                }
            })
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof HttpException) {
                    throw new RuntimeException(throwable.getCause());
                }
                throw new RuntimeException(new HttpException("Network error fetching profile for `" + username + "`", throwable));
            });
    }

    @NotNull
    public static MojangProfile getMojangProfile(UUID uniqueId) throws HttpException {
        return getMojangProfile(uniqueId.toString());
    }

    public static HypixelPlayerResponse getHypixelPlayer(UUID uniqueId) throws HttpException {
        String url = String.format("https://api.hypixel.net/player?uuid=%s", uniqueId);
        Summary.Timer requestTimer = PrometheusMetrics.HTTP_REQUEST_LATENCY.labels(url).startTimer();

        try {
            String hypixelApiKey = BotEnvironment.getHypixelAPIKey().map(UUID::toString).orElse("");
            return BotEnvironment.GSON.fromJson(getHttpResponse(url, Pair.of("API-Key", hypixelApiKey)).body(), HypixelPlayerResponse.class);
        } catch (Exception exception) {
            throw new HttpException("Unable to locate Hypixel Player for `" + uniqueId + "`", exception);
        } finally {
            requestTimer.observeDuration();
        }
    }

    public static CompletableFuture<HypixelPlayerResponse> getHypixelPlayerAsync(UUID uniqueId) {
        String url = String.format("https://api.hypixel.net/player?uuid=%s", uniqueId);
        Summary.Timer requestTimer = PrometheusMetrics.HTTP_REQUEST_LATENCY.labels(url).startTimer();

        String hypixelApiKey = BotEnvironment.getHypixelAPIKey().map(UUID::toString).orElse("");

        return getHttpResponseAsync(url, Pair.of("API-Key", hypixelApiKey))
            .thenApply(response -> {
                try {
                    return BotEnvironment.GSON.fromJson(response.body(), HypixelPlayerResponse.class);
                } catch (JsonSyntaxException exception) {
                    throw new RuntimeException(new HttpException("Invalid JSON response from Hypixel API for `" + uniqueId + "`", exception));
                } finally {
                    requestTimer.observeDuration();
                }
            })
            .exceptionally(throwable -> {
                requestTimer.observeDuration();
                if (throwable.getCause() instanceof HttpException) {
                    throw new RuntimeException(throwable.getCause());
                }
                throw new RuntimeException(new HttpException("Network error while fetching Hypixel player data for `" + uniqueId + "`", throwable));
            });
    }

    /**
     * Makes an HTTP request with Prometheus metrics tracking.
     * This method wraps the core HttpClient with monitoring capabilities.
     */
    private static HttpResponse<String> getHttpResponse(String url, Pair<String, String>... headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).GET();
        Arrays.stream(headers).forEach(h -> builder.header(h.getLeft(), h.getRight()));

        HttpRequest request = builder.build();
        log.info("Sending HTTP request to {} with headers {}", url, Arrays.toString(headers));
        PrometheusMetrics.HTTP_REQUESTS_AMOUNT.labels(request.method(), url).inc();

        return HttpClient.getClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static CompletableFuture<HttpResponse<String>> getHttpResponseAsync(String url, Pair<String, String>... headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET();
        Arrays.stream(headers).forEach(h -> builder.header(h.getLeft(), h.getRight()));

        HttpRequest request = builder.build();
        log.info("Sending async HTTP request to {} with headers {}", url, Arrays.toString(headers));
        PrometheusMetrics.HTTP_REQUESTS_AMOUNT.labels(request.method(), url).inc();

        return HttpClient.getClient().sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Makes a simple HTTP request and returns JSON.
     * Delegates to core HttpClient.
     */
    public static JsonObject makeHttpRequest(String url) throws IOException, InterruptedException {
        return HttpClient.getJson(url);
    }

    /**
     * Makes an asynchronous HTTP request and returns JSON.
     * Delegates to core HttpClient.
     */
    public static CompletableFuture<JsonObject> makeHttpRequestAsync(String url) {
        return HttpClient.getJsonAsync(url);
    }
}
