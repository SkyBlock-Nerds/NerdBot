package net.hypixel.nerdbot.app.generation.pack.update;

import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;

import java.util.List;

/** Reads the Hypixel resource pack list. */
public interface HypixelPackApiClient {

    /** Fetches and validates the shape of the pack list. Shape problems are failures, never exceptions. */
    Result<List<HypixelPack>, HttpException> fetchPacks();
}
