package net.hypixel.nerdbot.app.generation.pack.update;

import org.jetbrains.annotations.Nullable;

import java.net.URI;

/**
 * One downloadable zip of a pack release.
 *
 * @param packFormat The Minecraft resource pack format the zip targets
 * @param url        Where to download it
 * @param sha1       Lowercase hex SHA-1 the source publishes for the zip, or null when it publishes none
 */
public record PackReleaseVersion(int packFormat, URI url, @Nullable String sha1) {
}
