package net.hypixel.nerdbot.app.generation.pack.update;

import net.hypixel.nerdbot.app.config.GeneratorConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class PackFixtures {

    static final String PACK_ID = "hypixel:skyblock";
    static final String HYPIXEL_PACK_ID = "SkyBlock";

    private PackFixtures() {
    }

    /** A real resource pack zip; {@code marker} changes the bytes so each pack gets its own SHA-1. */
    static byte[] packZip(int packFormat, String marker) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("pack.mcmeta"));
            zip.write(("{\"pack\":{\"pack_format\":" + packFormat + ",\"description\":\"test\"}}").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("marker.txt"));
            zip.write(marker.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    static String sha1(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static Path write(Path file, byte[] bytes) {
        try {
            Files.createDirectories(file.getParent());
            return Files.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static GeneratorConfig.PackDefinition definition(Path configuredZip) {
        GeneratorConfig.PackAutoUpdate autoUpdate = new GeneratorConfig.PackAutoUpdate();
        autoUpdate.setEnabled(true);
        autoUpdate.setHypixelPackId(HYPIXEL_PACK_ID);
        autoUpdate.setMinItemRatio(0.5);
        autoUpdate.setSampleItems(List.of("minecraft:diamond_sword"));

        GeneratorConfig.PackDefinition definition = new GeneratorConfig.PackDefinition();
        definition.setId(PACK_ID);
        definition.setPath(configuredZip.toString());
        definition.setAutoUpdate(autoUpdate);
        return definition;
    }

    static GeneratorConfig.AutoUpdateSettings settings(Path cacheDir) {
        GeneratorConfig.AutoUpdateSettings settings = new GeneratorConfig.AutoUpdateSettings();
        settings.setCacheDir(cacheDir.toString());
        return settings;
    }
}
