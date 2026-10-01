package net.hypixel.nerdbot.app.generation.pack.update;

import java.net.URI;

/**
 * One downloadable zip of a Hypixel pack.
 *
 * @param packFormat The Minecraft resource pack format the zip targets
 * @param sha1       Lowercase 40-character hex SHA-1 of the zip
 * @param url        Where to download it
 */
public record HypixelPackVersion(int packFormat, String sha1, URI url) {
}
