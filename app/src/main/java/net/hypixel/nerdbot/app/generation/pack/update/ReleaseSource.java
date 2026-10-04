package net.hypixel.nerdbot.app.generation.pack.update;

import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;

/** Tells the updater about the latest release of a pack. Problems are failures, never exceptions. */
@FunctionalInterface
public interface ReleaseSource {

    Result<PackRelease, HttpException> latest();
}
