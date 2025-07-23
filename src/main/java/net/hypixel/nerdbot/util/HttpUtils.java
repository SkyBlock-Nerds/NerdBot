package net.hypixel.nerdbot.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.prometheus.client.Summary;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.util.exception.HttpException;
import net.hypixel.nerdbot.util.gson.HypixelPlayerResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Log4j2
public class HttpUtils {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private HttpUtils() {
    }

    public static MojangProfile getMojangProfile(String username) throws HttpException {
        String mojangUrl = String.format("https://api.mojang.com/users/profiles/minecraft/%s", username);
        String ashconUrl = String.format("https://api.ashcon.app/mojang/v2/user/%s", username);

        if (UUIDUtils.isUUID(username)) {
            mojangUrl = String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s", username);
        }

        try {
            String body = sendRequestWithFallback(mojangUrl, ashconUrl);
            return NerdBotApp.GSON.fromJson(body, MojangProfile.class);
        } catch (IOException | InterruptedException exception) {
            throw new HttpException("Network error fetching profile for `" + username + "`", exception);
        } catch (Exception exception) {
            throw new HttpException("Failed to parse Mojang profile for `" + username + "`: " + exception.getMessage(), exception);
        }
    }

    public static CompletableFuture<MojangProfile> getMojangProfileAsync(String username) {
        String mojangUrl = String.format("https://api.mojang.com/users/profiles/minecraft/%s", username);
        String ashconUrl = String.format("https://api.ashcon.app/mojang/v2/user/%s", username);

        if (UUIDUtils.isUUID(username)) {
            mojangUrl = String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s", mojangUrl);
        }

        return sendRequestWithFallbackAsync(mojangUrl, ashconUrl)
            .thenApply(body -> {
                try {
                    return NerdBotApp.GSON.fromJson(body, MojangProfile.class);
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
            String hypixelApiKey = NerdBotApp.getHypixelAPIKey().map(UUID::toString).orElse("");
            return NerdBotApp.GSON.fromJson(getHttpResponse(url, Pair.of("API-Key", hypixelApiKey)).body(), HypixelPlayerResponse.class);
        } catch (Exception exception) {
            throw new HttpException("Unable to locate Hypixel Player for `" + uniqueId + "`", exception);
        } finally {
            requestTimer.observeDuration();
        }
    }

    public static CompletableFuture<HypixelPlayerResponse> getHypixelPlayerAsync(UUID uniqueId) {
        String url = String.format("https://api.hypixel.net/player?uuid=%s", uniqueId);
        Summary.Timer requestTimer = PrometheusMetrics.HTTP_REQUEST_LATENCY.labels(url).startTimer();

        String hypixelApiKey = NerdBotApp.getHypixelAPIKey().map(UUID::toString).orElse("");

        return getHttpResponseAsync(url, Pair.of("API-Key", hypixelApiKey))
            .thenApply(response -> {
                try {
                    return NerdBotApp.GSON.fromJson(response.body(), HypixelPlayerResponse.class);
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

    @Nullable
    private static String sendRequestWithFallback(String primaryUrl, String fallbackUrl)
        throws IOException, InterruptedException, HttpException {
        HttpResponse<String> primary = getHttpResponse(primaryUrl);

        if (requestWasSuccessful(primary)) {
            return primary.body();
        }

        log.warn("Primary URL returned {}: {} (trying fallback URL)", primary.statusCode(), primary.body());
        HttpResponse<String> fallback = getHttpResponse(fallbackUrl);

        if (requestWasSuccessful(fallback)) {
            return fallback.body();
        }

        throw new HttpException(String.format("Both primary and fallback requests failed (primary: %d, fallback: %d)", primary.statusCode(), fallback.statusCode()));
    }

    private static CompletableFuture<String> sendRequestWithFallbackAsync(String primaryUrl, String fallbackUrl) {
        return getHttpResponseAsync(primaryUrl)
            .thenCompose(primary -> {
                if (requestWasSuccessful(primary)) {
                    return CompletableFuture.completedFuture(primary.body());
                }

                log.warn("Primary URL returned {}: {} (trying fallback URL)", primary.statusCode(), primary.body());

                return getHttpResponseAsync(fallbackUrl)
                    .thenApply(fallback -> {
                        if (requestWasSuccessful(fallback)) {
                            return fallback.body();
                        }

                        throw new RuntimeException(new HttpException(String.format("Both primary and fallback requests failed (primary: %d, fallback: %d)", primary.statusCode(), fallback.statusCode())));
                    });
            });
    }

    private static boolean requestWasSuccessful(HttpResponse<?> response) {
        int code = response.statusCode();
        return code >= 200 && code < 300;
    }

    private static HttpResponse<String> getHttpResponse(String url, Pair<String, String>... headers) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).GET();
        Arrays.stream(headers).forEach(h -> builder.header(h.getLeft(), h.getRight()));

        HttpRequest request = builder.build();
        log.info("Sending HTTP request to {} with headers {}", url, Arrays.toString(headers));
        PrometheusMetrics.HTTP_REQUESTS_AMOUNT.labels(request.method(), url).inc();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
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

        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    public static JsonObject makeHttpRequest(String url) throws IOException, InterruptedException {
        HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(String.format(url))).GET().build();
        String requestResponse;

        HttpResponse<String> response = HTTP_CLIENT.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        requestResponse = response.body();

        return NerdBotApp.GSON.fromJson(requestResponse, JsonObject.class);
    }

    public static CompletableFuture<JsonObject> makeHttpRequestAsync(String url) {
        HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(String.format(url))).GET().build();

        return HTTP_CLIENT.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
            .thenApply(HttpResponse::body)
            .thenApply(response -> NerdBotApp.GSON.fromJson(response, JsonObject.class));
    }
}