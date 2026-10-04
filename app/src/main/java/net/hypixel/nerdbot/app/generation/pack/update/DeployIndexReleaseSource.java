package net.hypixel.nerdbot.app.generation.pack.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.http.HttpClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * New versions from a deploy index: a JSON document naming the current deploy id and the pack
 * formats it was built for, for example
 * {"uuid":"3fe209f6-82c6-44c1-85f4-311c30dfe50b","pack_versions":[84,88,97],"last_updated":1790892317439}.
 * Download URLs come from the configured template. No SHA-1 is published, so the updater
 * identifies these zips by deploy id and format instead.
 */
public class DeployIndexReleaseSource implements ReleaseSource {

    /** Only a canonical UUID may go into a download URL, so the index cannot add path segments. */
    private static final Pattern UUID_PATTERN =
        Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final String indexUrl;
    private final String downloadUrlTemplate;

    public DeployIndexReleaseSource(String indexUrl, String downloadUrlTemplate) {
        this.indexUrl = indexUrl;
        this.downloadUrlTemplate = downloadUrlTemplate;
    }

    @Override
    public Result<PackRelease, HttpException> latest() {
        return HttpClient.getJson(indexUrl).flatMap(this::parse);
    }

    Result<PackRelease, HttpException> parse(JsonObject root) {
        try {
            JsonElement uuid = root.get("uuid");
            if (uuid == null || !uuid.isJsonPrimitive() || !uuid.getAsJsonPrimitive().isString()) {
                return failure("the index has no uuid");
            }
            String deployId = uuid.getAsString().trim();
            if (!UUID_PATTERN.matcher(deployId).matches()) {
                return failure("the index uuid is not a UUID: " + deployId);
            }

            JsonElement formats = root.get("pack_versions");
            if (formats == null || !formats.isJsonArray() || formats.getAsJsonArray().isEmpty()) {
                return failure("the index lists no pack_versions");
            }

            List<PackReleaseVersion> versions = new ArrayList<>();
            for (JsonElement element : formats.getAsJsonArray()) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                    return failure("pack_versions contains something that is not a number: " + element);
                }
                double value = element.getAsDouble();
                if (value <= 0 || value != Math.rint(value) || value > Integer.MAX_VALUE) {
                    return failure("pack_versions contains an invalid pack format: " + element);
                }
                int format = (int) value;
                versions.add(new PackReleaseVersion(format, URI.create(downloadUrl(downloadUrlTemplate, deployId, format)), null));
            }

            JsonElement lastUpdated = root.get("last_updated");
            long lastUpdatedMillis = lastUpdated != null && lastUpdated.isJsonPrimitive() && lastUpdated.getAsJsonPrimitive().isNumber()
                ? lastUpdated.getAsLong()
                : 0L;

            return Result.success(new PackRelease(deployId, lastUpdatedMillis, versions));
        } catch (RuntimeException e) {
            // URI.create and Gson getAs* throw unchecked exceptions on bad values
            return failure("the index response is malformed: " + e.getMessage());
        }
    }

    /** The template with both placeholders filled in. */
    static String downloadUrl(String template, String deployId, int format) {
        return template
            .replace(ReleaseSources.DEPLOY_ID_PLACEHOLDER, deployId)
            .replace(ReleaseSources.FORMAT_PLACEHOLDER, String.valueOf(format));
    }

    private Result<PackRelease, HttpException> failure(String message) {
        return Result.failure(new HttpException("Pack index " + indexUrl + ": " + message));
    }
}
