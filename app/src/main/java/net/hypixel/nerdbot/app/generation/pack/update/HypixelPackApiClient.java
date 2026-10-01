package net.hypixel.nerdbot.app.generation.pack.update;

import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.http.DownloadResult;

import java.nio.file.Path;
import java.util.List;

/** Everything the pack updater needs from the network, behind one seam. */
public interface HypixelPackApiClient {

    /** Fetches and validates the shape of the pack list. Shape problems are failures, never exceptions. */
    Result<List<HypixelPack>, HttpException> fetchPacks();

    /**
     * Downloads a version's zip to {@code target}. Host and size refusals fail with
     * {@link net.hypixel.nerdbot.marmalade.exception.DownloadRejectedException}.
     */
    Result<DownloadResult, HttpException> download(HypixelPackVersion version, Path target);
}
