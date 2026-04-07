package net.hypixel.nerdbot.discord.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ResourcePackConfig {

    /**
     * Directory to scan for resource pack zip files.
     * Each zip's filename (minus extension) becomes its pack name.
     */
    private String packDirectory = "./resource-packs";

    /**
     * Name of the default resource pack to use when a user doesn't specify one.
     * Null means vanilla atlas only.
     */
    private String defaultPack = null;

    /**
     * Whether to chain the built-in vanilla texture atlas as fallback behind resource packs.
     * Almost always true since most packs only override textures, not models.
     */
    private boolean includeVanillaFallback = true;
}
