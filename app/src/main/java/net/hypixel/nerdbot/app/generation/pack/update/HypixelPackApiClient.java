package net.hypixel.nerdbot.app.generation.pack.update;

import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;

import java.util.List;

/** Everything the pack updater needs from the network, behind one seam. */
public interface HypixelPackApiClient {

    /** Fetches and validates the shape of the pack list. Shape problems are failures, never exceptions. */
    Result<List<HypixelPack>, HttpException> fetchPacks();
}
