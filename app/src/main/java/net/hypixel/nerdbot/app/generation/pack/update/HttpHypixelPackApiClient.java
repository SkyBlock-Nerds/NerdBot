package net.hypixel.nerdbot.app.generation.pack.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.app.config.GeneratorConfig;
import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.http.DownloadResult;
import net.hypixel.nerdbot.marmalade.http.HttpClient;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Talks to the Hypixel resource pack API and its CDN. */
public class HttpHypixelPackApiClient implements HypixelPackApiClient {

    private static final Pattern SHA1_HEX = Pattern.compile("[0-9a-f]{40}");

    private final String apiUrl;
    private final long maxDownloadBytes;
    private final Set<String> allowedHosts;

    public HttpHypixelPackApiClient(GeneratorConfig.AutoUpdateSettings settings) {
        this.apiUrl = settings.getApiUrl();
        this.maxDownloadBytes = settings.getMaxDownloadBytes();
        this.allowedHosts = settings.getAllowedDownloadHosts().stream()
            .map(host -> host.trim().toLowerCase(Locale.ROOT))
            .filter(host -> !host.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Result<List<HypixelPack>, HttpException> fetchPacks() {
        return HttpClient.getJson(apiUrl).flatMap(HttpHypixelPackApiClient::parse);
    }

    @Override
    public Result<DownloadResult, HttpException> download(HypixelPackVersion version, Path target) {
        return HttpClient.downloadToFile(version.url(), target, maxDownloadBytes, allowedHosts);
    }

    static Result<List<HypixelPack>, HttpException> parse(JsonObject root) {
        try {
            if (!root.has("success") || !root.get("success").getAsBoolean()) {
                return failure("the pack list response did not report success");
            }

            if (!root.has("packs") || !root.get("packs").isJsonArray()) {
                return failure("the pack list response has no packs array");
            }

            List<HypixelPack> packs = new ArrayList<>();
            for (JsonElement packElement : root.getAsJsonArray("packs")) {
                JsonObject pack = packElement.getAsJsonObject();
                List<HypixelPackVersion> versions = new ArrayList<>();

                for (JsonElement versionElement : pack.getAsJsonArray("versions")) {
                    JsonObject version = versionElement.getAsJsonObject();
                    int packFormat = version.get("packFormat").getAsInt();
                    String sha1 = version.get("hash").getAsString().toLowerCase(Locale.ROOT);
                    String url = version.get("url").getAsString();

                    if (packFormat <= 0) {
                        return failure("a version of pack " + pack.get("id") + " has pack format " + packFormat);
                    }
                    if (!SHA1_HEX.matcher(sha1).matches()) {
                        return failure("a version of pack " + pack.get("id") + " has a malformed hash: " + sha1);
                    }

                    versions.add(new HypixelPackVersion(packFormat, sha1, URI.create(url)));
                }

                packs.add(new HypixelPack(
                    pack.get("id").getAsString(),
                    pack.has("deployId") ? pack.get("deployId").getAsString() : "unknown",
                    pack.has("lastUpdated") ? pack.get("lastUpdated").getAsLong() : 0L,
                    versions
                ));
            }

            return Result.success(List.copyOf(packs));
        } catch (RuntimeException e) {
            // Gson getAs* and URI.create throw unchecked exceptions on wrong types and bad URLs
            return failure("the pack list response is malformed: " + e.getMessage());
        }
    }

    private static Result<List<HypixelPack>, HttpException> failure(String message) {
        return Result.failure(new HttpException("Hypixel pack API: " + message));
    }
}
