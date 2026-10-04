package net.hypixel.nerdbot.app.generation.pack.update;

import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;

import java.util.List;
import java.util.Optional;

/** New versions from the official Hypixel pack list, which publishes a SHA-1 for every zip. */
public class HypixelApiReleaseSource implements ReleaseSource {

    private final HypixelPackApiClient client;
    private final String hypixelPackId;

    public HypixelApiReleaseSource(HypixelPackApiClient client, String hypixelPackId) {
        this.client = client;
        this.hypixelPackId = hypixelPackId;
    }

    @Override
    public Result<PackRelease, HttpException> latest() {
        Result<List<HypixelPack>, HttpException> fetched = client.fetchPacks();
        if (fetched instanceof Result.Failure<List<HypixelPack>, HttpException> failure) {
            return Result.failure(new HttpException("could not fetch the pack list: " + failure.error().getMessage(), failure.error()));
        }

        Optional<HypixelPack> pack = fetched.orElseThrow().stream()
            .filter(candidate -> candidate.id().equals(hypixelPackId))
            .findFirst();
        if (pack.isEmpty()) {
            return failure("the pack list has no entry named " + hypixelPackId);
        }
        if (pack.get().versions().isEmpty()) {
            return failure("pack " + hypixelPackId + " lists no versions");
        }

        List<PackReleaseVersion> versions = pack.get().versions().stream()
            .map(version -> new PackReleaseVersion(version.packFormat(), version.url(), version.sha1()))
            .toList();
        return Result.success(new PackRelease(pack.get().deployId(), pack.get().lastUpdated(), versions));
    }

    private static Result<PackRelease, HttpException> failure(String message) {
        return Result.failure(new HttpException(message));
    }
}
