package net.hypixel.nerdbot.util;

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
import java.util.Arrays;
import java.util.UUID;

@Log4j2
public class HttpUtils {

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
}