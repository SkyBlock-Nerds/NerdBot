package net.hypixel.nerdbot.generator.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.BotEnvironment;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public final class SimpleHttpClient {

    private static final OkHttpClient CLIENT = new OkHttpClient();

    private SimpleHttpClient() {
    }

    public static JsonObject getJson(String url) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Request failed with code " + response.code());
            }

            ResponseBody body = response.body();
            JsonElement element = BotEnvironment.GSON.fromJson(body.charStream(), JsonElement.class);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        }
    }

    public static CompletableFuture<JsonObject> getJsonAsync(String url) {
        Request request = new Request.Builder().url(url).get().build();
        CompletableFuture<JsonObject> future = new CompletableFuture<>();

        CLIENT.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException exception) {
                future.completeExceptionally(exception);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (response; ResponseBody body = response.body()) {
                    if (!response.isSuccessful()) {
                        future.completeExceptionally(new IOException("Request failed with code " + response.code()));
                        return;
                    }

                    JsonElement element = BotEnvironment.GSON.fromJson(body.charStream(), JsonElement.class);
                    JsonObject jsonObject = element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
                    future.complete(jsonObject);
                } catch (Exception exception) {
                    future.completeExceptionally(exception);
                }
            }
        });

        return future;
    }
}