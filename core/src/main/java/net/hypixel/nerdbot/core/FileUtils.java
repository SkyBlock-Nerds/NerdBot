package net.hypixel.nerdbot.core;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class FileUtils {

    public static final DateTimeFormatter REGULAR_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss ZZZ").withZone(ZoneId.systemDefault());
    public static final DateTimeFormatter FILE_NAME_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    private FileUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static String getBranchName() {
        String branchName = System.getenv("BRANCH_NAME");
        return branchName == null || branchName.isBlank() ? "unknown" : branchName;
    }

    public static String getDockerContainerId() {
        try {
            return Files.readString(Path.of("/etc/hostname")).trim();
        } catch (IOException e) {
            log.error("Failed to read Docker container ID from /etc/hostname", e);
            return "unknown";
        }
    }

    public static File createTempFile(String fileName, String content) throws IOException {
        String dir = System.getProperty("java.io.tmpdir");
        File file = new File(dir + File.separator + fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        log.info("Created temporary file " + file.getAbsolutePath());
        return file;
    }

    public static CompletableFuture<File> createTempFileAsync(String fileName, String content) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return createTempFile(fileName, content);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static File toFile(BufferedImage imageToSave) throws IOException {
        File tempFile = File.createTempFile("image", ".png");
        ImageIO.write(imageToSave, "PNG", tempFile);
        return tempFile;
    }

    public static CompletableFuture<File> toFileAsync(BufferedImage imageToSave) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return toFile(imageToSave);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}