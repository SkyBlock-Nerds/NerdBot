package net.hypixel.skyblocknerds.utilities;

import lombok.NonNull;
import lombok.SneakyThrows;

import java.io.File;

public class Utilities {

    /**
     * Get the current directory of the running jar
     * @return {@link File} of the current directory
     */
    @SneakyThrows
    public static @NonNull File getCurrentDirectory() {
        return new File(Utilities.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
    }
}