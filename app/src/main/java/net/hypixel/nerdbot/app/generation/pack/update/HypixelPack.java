package net.hypixel.nerdbot.app.generation.pack.update;

import java.util.List;

/**
 * One entry of the Hypixel resource pack list.
 *
 * @param id          Hypixel's pack name, e.g. SkyBlock
 * @param deployId    The deployment the versions belong to
 * @param lastUpdated Epoch millis of the deployment
 * @param versions    One downloadable zip per pack format, unordered
 */
public record HypixelPack(String id, String deployId, long lastUpdated, List<HypixelPackVersion> versions) {

    public HypixelPack {
        versions = List.copyOf(versions);
    }
}
