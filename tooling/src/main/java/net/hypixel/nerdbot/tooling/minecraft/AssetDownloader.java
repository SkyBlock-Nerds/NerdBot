package net.hypixel.nerdbot.tooling.minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.core.HttpClient;
import net.hypixel.nerdbot.tooling.ToolingConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Utility class for downloading Minecraft assets.
 *
 * @see MinecraftAssetTool
 */
public class AssetDownloader {

    private static final String VERSION_MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String ASSETS_PREFIX = "assets/minecraft/";

    private final String version;
    private final Path outputPath;
    private final boolean keepJar;
    private final boolean clean;
    private String resolvedVersion;

    /**
     * Creates a new AssetDownloader for downloading and extracting Minecraft assets.
     *
     * @param version    the Minecraft version to download, or null to use the latest release
     * @param outputPath the directory where assets will be extracted
     * @param keepJar    if true, keep the downloaded JAR file after extraction
     * @param clean      if true, delete the output directory before extraction
     */
    public AssetDownloader(String version, Path outputPath, boolean keepJar, boolean clean) {
        this.version = version;
        this.outputPath = outputPath;
        this.keepJar = keepJar;
        this.clean = clean;
    }

    public void setDebug(boolean debug) {
        ToolingConstants.setDebugEnabled(debug);
    }

    /**
     * Gets the resolved Minecraft version after {@link #download()} has been called.
     * If a specific version was provided in the constructor, returns that version.
     * If no version was provided, returns the latest release version that was fetched from the API.
     *
     * @return the resolved Minecraft version, or null if download() hasn't been called yet
     */
    public String getResolvedVersion() {
        return resolvedVersion;
    }

    /**
     * Downloads and extracts Minecraft assets.
     *
     * @return the number of files extracted
     */
    public int download() throws IOException, InterruptedException {
        this.resolvedVersion = resolveVersion();
        System.out.println("Downloading Minecraft " + resolvedVersion + " assets...");

        String clientJarUrl = getClientJarUrl(resolvedVersion);
        ToolingConstants.printDebug("Client JAR URL: " + clientJarUrl);

        Path jarPath = downloadClientJar(clientJarUrl, resolvedVersion);
        System.out.println("Downloaded client JAR: " + jarPath);

        String mappingsUrl = getMappingsUrl(resolvedVersion);
        if (mappingsUrl != null) {
            Path mappingsPath = downloadMappings(mappingsUrl, resolvedVersion);
            System.out.println("Downloaded mappings: " + mappingsPath);
        } else {
            ToolingConstants.printDebug("No mappings available for this version");
        }

        if (clean && Files.exists(outputPath)) {
            System.out.println("Cleaning output directory: " + outputPath);
            ToolingConstants.deleteDirectory(outputPath);
        }

        int count = extractAssets(jarPath);
        System.out.println("Extracted " + count + " files to: " + outputPath);

        if (!keepJar) {
            Files.deleteIfExists(jarPath);
            ToolingConstants.printDebug("Deleted JAR file");
        }

        return count;
    }

    /**
     * Resolves the Minecraft version to use.
     * Returns the version provided in the constructor, or fetches the latest release from Mojang's API.
     */
    private String resolveVersion() throws IOException, InterruptedException {
        if (version != null && !version.isEmpty()) {
            return version;
        }

        System.out.println("Fetching version manifest...");
        JsonObject manifest = HttpClient.getJson(VERSION_MANIFEST_URL);
        JsonObject latest = manifest.getAsJsonObject("latest");
        String latestRelease = latest.get("release").getAsString();
        System.out.println("Latest release: " + latestRelease);

        return latestRelease;
    }

    /**
     * Fetches the client JAR download URL for the specified version from Mojang's API.
     */
    private String getClientJarUrl(String targetVersion) throws IOException, InterruptedException {
        JsonObject manifest = HttpClient.getJson(VERSION_MANIFEST_URL);
        JsonArray versions = manifest.getAsJsonArray("versions");

        for (int i = 0; i < versions.size(); i++) {
            JsonObject versionEntry = versions.get(i).getAsJsonObject();
            String versionId = versionEntry.get("id").getAsString();

            if (versionId.equals(targetVersion)) {
                String versionUrl = versionEntry.get("url").getAsString();
                ToolingConstants.printDebug("Version manifest URL: " + versionUrl);

                JsonObject versionMeta = HttpClient.getJson(versionUrl);
                JsonObject client = versionMeta.getAsJsonObject("downloads").getAsJsonObject("client");

                return client.get("url").getAsString();
            }
        }

        throw new IllegalArgumentException("Version not found: " + targetVersion);
    }

    /**
     * Fetches the client mappings download URL for the specified version from Mojang's API.
     * Returns null if mappings are not available for the version (e.g. older versions).
     */
    private String getMappingsUrl(String targetVersion) throws IOException, InterruptedException {
        JsonObject manifest = HttpClient.getJson(VERSION_MANIFEST_URL);
        JsonArray versions = manifest.getAsJsonArray("versions");

        for (int i = 0; i < versions.size(); i++) {
            JsonObject versionEntry = versions.get(i).getAsJsonObject();
            String versionId = versionEntry.get("id").getAsString();

            if (versionId.equals(targetVersion)) {
                String versionUrl = versionEntry.get("url").getAsString();
                JsonObject versionMeta = HttpClient.getJson(versionUrl);
                JsonObject downloads = versionMeta.getAsJsonObject("downloads");
                if (downloads.has("client_mappings")) {
                    JsonObject clientMappings = downloads.getAsJsonObject("client_mappings");
                    return clientMappings.get("url").getAsString();
                }

                return null;
            }
        }

        return null;
    }

    /**
     * Downloads the client mappings file, or returns the cached path if already downloaded.
     */
    private Path downloadMappings(String url, String versionId) throws IOException, InterruptedException {
        Files.createDirectories(ToolingConstants.MAPPINGS_CACHE);

        Path mappingsPath = ToolingConstants.getMappingsPath(versionId);

        if (Files.exists(mappingsPath)) {
            System.out.println("Using cached mappings: " + mappingsPath);
            return mappingsPath;
        }

        System.out.println("Downloading mappings (" + versionId + ")...");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();

        HttpResponse<InputStream> response = HttpClient.getClient()
            .send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Failed to download mappings. Status: " + response.statusCode());
        }

        try (InputStream in = response.body()) {
            Files.copy(in, mappingsPath, StandardCopyOption.REPLACE_EXISTING);
        }

        return mappingsPath;
    }

    /**
     * Downloads the client JAR file with progress reporting, or returns the cached path if already downloaded.
     */
    private Path downloadClientJar(String url, String versionId) throws IOException, InterruptedException {
        Files.createDirectories(ToolingConstants.JAR_CACHE);

        Path jarPath = ToolingConstants.getJarPath(versionId);

        if (Files.exists(jarPath)) {
            System.out.println("Using cached JAR: " + jarPath);
            return jarPath;
        }

        System.out.println("Downloading client JAR (" + versionId + ")...");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();

        HttpResponse<InputStream> response = HttpClient.getClient()
            .send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Failed to download JAR. Status: " + response.statusCode());
        }

        // Get content length for progress
        long contentLength = response.headers()
            .firstValueAsLong("Content-Length")
            .orElse(-1);

        try (InputStream in = response.body()) {
            Path tempFile = ToolingConstants.JAR_CACHE.resolve("download-" + System.currentTimeMillis() + ".tmp");
            long totalRead = 0;
            byte[] buffer = new byte[8_192];
            int read;

            try (OutputStream out = Files.newOutputStream(tempFile)) {
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    totalRead += read;

                    if (contentLength > 0) {
                        double percent = (totalRead * 100.0) / contentLength;
                        System.out.printf("\rDownloading: %.1f MB / %.1f MB (%.1f%%)",
                            totalRead / 1_000_000.0,
                            contentLength / 1_000_000.0,
                            percent
                        );
                    } else {
                        System.out.printf("\rDownloading: %.1f MB", totalRead / 1_000_000.0);
                    }
                }
            }
            System.out.println();

            Files.move(tempFile, jarPath, StandardCopyOption.REPLACE_EXISTING);
        }

        return jarPath;
    }

    /**
     * Extracts the assets/minecraft folder from the JAR to the output directory.
     *
     * @return the number of files extracted
     */
    private int extractAssets(Path jarPath) throws IOException {
        int count = 0;

        try (ZipFile zip = new ZipFile(jarPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();

                if (!entryName.startsWith(ASSETS_PREFIX)) {
                    continue;
                }

                if (entry.isDirectory()) {
                    continue;
                }

                String relativeName = entryName.substring(ASSETS_PREFIX.length());
                Path outputFile = outputPath.resolve(relativeName);

                Files.createDirectories(outputFile.getParent());

                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, outputFile, StandardCopyOption.REPLACE_EXISTING);
                }

                count++;
                if (count % 100 == 0) {
                    System.out.printf("\rExtracting assets: %d", count);
                }
            }
        }

        System.out.printf("\rExtracting assets: %d%n", count);
        return count;
    }

}