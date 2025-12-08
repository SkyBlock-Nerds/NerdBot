package net.hypixel.nerdbot.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class HttpClient {

    private static final Gson GSON = new GsonBuilder().create();

    private static final java.net.http.HttpClient CLIENT = java.net.http.HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
        .build();

    private HttpClient() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Makes a synchronous HTTP GET request and returns the response body as a string.
     *
     * @param url The URL to request
     * @return The response body as a string
     * @throws IOException If an I/O error occurs
     * @throws InterruptedException If the operation is interrupted
     */
    @NotNull
    public static String getString(@NotNull String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP request failed with status code " + response.statusCode() + " for URL: " + url);
        }
        
        return response.body();
    }

    /**
     * Makes an asynchronous HTTP GET request and returns the response body as a string.
     *
     * @param url The URL to request
     * @return A CompletableFuture that will complete with the response body
     */
    @NotNull
    public static CompletableFuture<String> getStringAsync(@NotNull String url) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new RuntimeException(new IOException("HTTP request failed with status code " + response.statusCode() + " for URL: " + url));
                }
                return response.body();
            });
    }

    /**
     * Makes a synchronous HTTP GET request and parses the response as JSON.
     *
     * @param url The URL to request
     * @return The parsed JSON object
     * @throws IOException If an I/O error occurs or the response is not valid JSON
     * @throws InterruptedException If the operation is interrupted
     */
    @NotNull
    public static JsonObject getJson(@NotNull String url) throws IOException, InterruptedException {
        String responseBody = getString(url);
        JsonElement element = GSON.fromJson(responseBody, JsonElement.class);
        
        if (element == null || !element.isJsonObject()) {
            return new JsonObject();
        }
        
        return element.getAsJsonObject();
    }

    /**
     * Makes an asynchronous HTTP GET request and parses the response as JSON.
     *
     * @param url The URL to request
     * @return A CompletableFuture that will complete with the parsed JSON object
     */
    @NotNull
    public static CompletableFuture<JsonObject> getJsonAsync(@NotNull String url) {
        return getStringAsync(url)
            .thenApply(body -> {
                JsonElement element = GSON.fromJson(body, JsonElement.class);
                
                if (element == null || !element.isJsonObject()) {
                    return new JsonObject();
                }
                
                return element.getAsJsonObject();
            });
    }

    /**
     * Gets the underlying HttpClient instance for advanced usage.
     * Modules can use this for custom request building.
     *
     * @return The shared HttpClient instance
     */
    @NotNull
    public static java.net.http.HttpClient getClient() {
        return CLIENT;
    }
}