package net.hypixel.nerdbot.app.generation.pack.update;

import net.hypixel.nerdbot.app.config.GeneratorConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * A short digest of every setting that can make a pack fail the update checks: the download host
 * allowlist and size cap, and the pack's validation settings (minimum item ratio and sample items).
 * A rejection recorded under one fingerprint stops applying once these settings change, so fixing
 * a config mistake lets the bot try the same pack again instead of blocking it until Hypixel
 * publishes a new one. Values are normalised (trimmed, hosts lowercased, sorted, de-duplicated) so
 * reordering a list does not count as a change.
 */
public final class RejectionFingerprint {

    private static final int LENGTH = 16;

    private RejectionFingerprint() {
    }

    public static String of(GeneratorConfig.AutoUpdateSettings settings, GeneratorConfig.PackAutoUpdate autoUpdate) {
        String canonical = "hosts=" + normalised(settings.getAllowedDownloadHosts(), true)
            + "\nmaxDownloadBytes=" + settings.getMaxDownloadBytes()
            + "\nminItemRatio=" + autoUpdate.getMinItemRatio()
            + "\nsampleItems=" + normalised(autoUpdate.getSampleItems(), false);

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", e);
        }
    }

    private static String normalised(List<String> values, boolean lowercase) {
        if (values == null) {
            return "";
        }
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .map(value -> lowercase ? value.toLowerCase(Locale.ROOT) : value)
            .distinct()
            .sorted()
            .collect(Collectors.joining(","));
    }
}
