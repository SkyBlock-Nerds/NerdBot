package net.hypixel.nerdbot.app.generation.pack.update;

import java.util.List;

/**
 * The latest release of a pack, as its source reports it.
 *
 * @param deployId    The deployment the versions belong to
 * @param lastUpdated Epoch millis of the deployment, 0 when the source does not say
 * @param versions    One downloadable zip per pack format, never empty
 */
public record PackRelease(String deployId, long lastUpdated, List<PackReleaseVersion> versions) {

    public PackRelease {
        versions = List.copyOf(versions);
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("A release needs at least one version");
        }
    }
}
