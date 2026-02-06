package net.hypixel.nerdbot.tooling.minecraft;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Container for all color data extracted from Minecraft JAR.
 */
public class ExtractedColors {
    public Map<String, DyeColorData> dyeColors = new LinkedHashMap<>();
    public Map<String, Integer> effectColors = new LinkedHashMap<>();
    public Map<String, Integer> potionColors = new LinkedHashMap<>();
    public Set<String> potionNames = new HashSet<>();
}