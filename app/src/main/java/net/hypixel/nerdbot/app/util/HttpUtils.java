package net.hypixel.nerdbot.app.util;

import com.google.gson.JsonObject;
import io.prometheus.client.Summary;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.functional.Pair;
import net.hypixel.nerdbot.app.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.http.HttpClient;
import net.hypixel.nerdbot.marmalade.UUIDUtils;
import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.json.HypixelPlayerResponse;
import net.hypixel.nerdbot.marmalade.resilience.Retry;
import net.hypixel.nerdbot.discord.BotEnvironment;
import net.hypixel.nerdbot.marmalade.storage.database.model.user.stats.MojangProfile;
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
@UtilityClass
public class HttpUtils {

    private static final int RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_DELAY = Duration.ofMillis(500);
    private static final double RETRY_BACKOFF = 2.0;

    public static Result<MojangProfile, HttpException> getMojangProfile(String username) {
        String mojangUrl = buildMojangUrl(username);

        return Result.of(() -> Retry.<MojangProfile>of(() -> {
                String body = HttpClient.getString(mojangUrl).orElseThrow();
                return BotEnvironment.GSON.fromJson(body, MojangProfile.class);
            })
            .maxAttempts(RETRY_ATTEMPTS)
            .delay(RETRY_DELAY)
            .backoffMultiplier(RETRY_BACKOFF)
            .retryOn(HttpException.class)
            .execute()
        );
    }

    public static CompletableFuture<Result<MojangProfile, HttpException>> getMojangProfileAsync(String username) {
        String mojangUrl = buildMojangUrl(username);

        return Retry.<MojangProfile>of(() -> {
                String body = HttpClient.getString(mojangUrl).orElseThrow();
                return BotEnvironment.GSON.fromJson(body, MojangProfile.class);
            })
            .maxAttempts(RETRY_ATTEMPTS)
            .delay(RETRY_DELAY)
            .backoffMultiplier(RETRY_BACKOFF)
            .retryOn(HttpException.class)
            .executeAsync()
            .handle((profile, throwable) -> {
                if (throwable != null) {
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    if (cause instanceof HttpException httpException) {
                        return Result.failure(httpException);
                    }
                    return Result.failure(new HttpException("Failed to fetch Mojang profile for `" + username + "`", cause));
                }
                return Result.success(profile);
            });
    }

    @NotNull
    public static Result<MojangProfile, HttpException> getMojangProfile(UUID uniqueId) {
        return getMojangProfile(uniqueId.toString());
    }

    public static Result<HypixelPlayerResponse, HttpException> getHypixelPlayer(UUID uniqueId) {
        String url = String.format("https://api.hypixel.net/player?uuid=%s", uniqueId);
        Summary.Timer requestTimer = PrometheusMetrics.HTTP_REQUEST_LATENCY.labels(url).startTimer();

        try {
            return Result.of(() -> Retry.<HypixelPlayerResponse>of(() -> {
                    String hypixelApiKey = BotEnvironment.getHypixelAPIKey().map(UUID::toString).orElse("");
                    HttpResponse<String> response = getHttpResponse(url, Pair.of("API-Key", hypixelApiKey));
                    return BotEnvironment.GSON.fromJson(response.body(), HypixelPlayerResponse.class);
                })
                .maxAttempts(RETRY_ATTEMPTS)
                .delay(RETRY_DELAY)
                .backoffMultiplier(RETRY_BACKOFF)
                .execute()
            );
        } finally {
            requestTimer.observeDuration();
        }
    }

    public static CompletableFuture<Result<HypixelPlayerResponse, HttpException>> getHypixelPlayerAsync(UUID uniqueId) {
        String url = String.format("https://api.hypixel.net/player?uuid=%s", uniqueId);
        Summary.Timer requestTimer = PrometheusMetrics.HTTP_REQUEST_LATENCY.labels(url).startTimer();

        String hypixelApiKey = BotEnvironment.getHypixelAPIKey().map(UUID::toString).orElse("");

        return Retry.<HypixelPlayerResponse>of(() -> {
                HttpResponse<String> response = getHttpResponse(url, Pair.of("API-Key", hypixelApiKey));
                return BotEnvironment.GSON.fromJson(response.body(), HypixelPlayerResponse.class);
            })
            .maxAttempts(RETRY_ATTEMPTS)
            .delay(RETRY_DELAY)
            .backoffMultiplier(RETRY_BACKOFF)
            .executeAsync()
            .handle((player, throwable) -> {
                requestTimer.observeDuration();
                if (throwable != null) {
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    if (cause instanceof HttpException httpException) {
                        return Result.failure(httpException);
                    }
                    return Result.failure(new HttpException("Failed to fetch Hypixel player for `" + uniqueId + "`", cause));
                }
                return Result.success(player);
            });
    }

    /**
     * Makes an HTTP request with Prometheus metrics tracking.
     * This method wraps the core HttpClient with monitoring capabilities.
     */
    private static HttpResponse<String> getHttpResponse(String url, Pair<String, String>... headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).GET();
        Arrays.stream(headers).forEach(h -> builder.header(h.first(), h.second()));

        HttpRequest request = builder.build();
        log.info("Sending HTTP request to {} with {} header(s)", url, headers.length);
        PrometheusMetrics.HTTP_REQUESTS_AMOUNT.labels(request.method(), url).inc();

        return HttpClient.getClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static CompletableFuture<HttpResponse<String>> getHttpResponseAsync(String url, Pair<String, String>... headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET();
        Arrays.stream(headers).forEach(h -> builder.header(h.first(), h.second()));

        HttpRequest request = builder.build();
        log.info("Sending async HTTP request to {} with {} header(s)", url, headers.length);
        PrometheusMetrics.HTTP_REQUESTS_AMOUNT.labels(request.method(), url).inc();

        return HttpClient.getClient().sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Makes a simple HTTP request and returns JSON.
     * Delegates to core HttpClient.
     */
    public static Result<JsonObject, HttpException> makeHttpRequest(String url) {
        return HttpClient.getJson(url);
    }

    /**
     * Makes an asynchronous HTTP request and returns JSON.
     * Delegates to core HttpClient.
     */
    public static CompletableFuture<Result<JsonObject, HttpException>> makeHttpRequestAsync(String url) {
        return HttpClient.getJsonAsync(url);
    }

    private static String buildMojangUrl(String username) {
        if (UUIDUtils.isUUID(username)) {
            return String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s", username);
        }
        return String.format("https://api.mojang.com/users/profiles/minecraft/%s", username);
    }
}
