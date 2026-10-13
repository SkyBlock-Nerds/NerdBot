package net.hypixel.nerdbot.app.export;

import net.dv8tion.jda.api.utils.FileUpload;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * An export held in memory and uploaded from there, so nothing is left behind in the temp directory.
 * Discord's upload limit keeps these small enough to hold.
 */
public record ExportFile(String name, byte[] data) {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneOffset.UTC);

    /**
     * Names the file {@code <name>-<UTC time>.<extension>}. Anything in {@code name} other than
     * letters, digits, dots, dashes and underscores becomes an underscore, since channel and
     * category names can hold characters a file name can't.
     */
    public static ExportFile of(String name, String extension, Instant at, String content) {
        return of(name, extension, at, content.getBytes(StandardCharsets.UTF_8));
    }

    public static ExportFile of(String name, String extension, Instant at, byte[] data) {
        return new ExportFile(safeName(name) + "-" + STAMP.format(at) + "." + extension, data);
    }

    static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public FileUpload upload() {
        return FileUpload.fromData(data, name);
    }
}
