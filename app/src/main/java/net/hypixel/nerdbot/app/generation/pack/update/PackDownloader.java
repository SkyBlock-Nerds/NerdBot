package net.hypixel.nerdbot.app.generation.pack.update;

import net.hypixel.nerdbot.app.config.GeneratorConfig;
import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.http.DownloadResult;
import net.hypixel.nerdbot.marmalade.http.HttpClient;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Downloads pack zips through Marmalade, only from the allowed hosts and up to the size cap. Host
 * and size refusals fail with {@link net.hypixel.nerdbot.marmalade.exception.DownloadRejectedException}.
 */
public class PackDownloader {

    private final long maxDownloadBytes;
    private final Set<String> allowedHosts;

    public PackDownloader(GeneratorConfig.AutoUpdateSettings settings) {
        this.maxDownloadBytes = settings.getMaxDownloadBytes();
        List<String> configuredHosts = settings.getAllowedDownloadHosts() == null ? List.of() : settings.getAllowedDownloadHosts();
        this.allowedHosts = configuredHosts.stream()
            .filter(Objects::nonNull)
            .map(host -> host.trim().toLowerCase(Locale.ROOT))
            .filter(host -> !host.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    public Result<DownloadResult, HttpException> download(URI url, Path target) {
        return HttpClient.downloadToFile(url, target, maxDownloadBytes, allowedHosts);
    }
}
