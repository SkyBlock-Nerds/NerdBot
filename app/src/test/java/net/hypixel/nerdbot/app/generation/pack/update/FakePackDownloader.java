package net.hypixel.nerdbot.app.generation.pack.update;

import net.hypixel.nerdbot.app.config.GeneratorConfig;
import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.http.DownloadResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Serves canned bytes or failures per URL instead of going over the network. */
class FakePackDownloader extends PackDownloader {

    private final Map<URI, byte[]> bodies = new HashMap<>();
    private final Map<URI, HttpException> failures = new HashMap<>();
    final List<URI> requested = new ArrayList<>();

    FakePackDownloader() {
        super(new GeneratorConfig.AutoUpdateSettings());
    }

    FakePackDownloader serve(URI url, byte[] body) {
        bodies.put(url, body);
        failures.remove(url);
        return this;
    }

    /** Fails after writing half a file, like a download that drops partway through. */
    FakePackDownloader fail(URI url, HttpException error) {
        failures.put(url, error);
        bodies.remove(url);
        return this;
    }

    @Override
    public Result<DownloadResult, HttpException> download(URI url, Path target) {
        requested.add(url);
        try {
            if (failures.containsKey(url)) {
                Files.write(target, new byte[]{1, 2, 3});
                return Result.failure(failures.get(url));
            }

            byte[] body = bodies.get(url);
            if (body == null) {
                return Result.failure(new HttpException("no canned response for " + url));
            }
            Files.write(target, body);
            return Result.success(new DownloadResult(target, body.length, PackFixtures.sha1(body), url));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
