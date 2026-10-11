package net.hypixel.nerdbot.app.config;

import java.io.IOException;
import java.nio.file.Path;

/** The config file and the live config the bot runs with, kept in step. */
public interface ConfigStore {

    Path file();

    /**
     * Replaces the live config with the file's.
     *
     * @throws IllegalStateException when the file can't be read or parsed; the live config is left alone
     */
    void reload();

    /** Writes {@code config} to the file atomically and makes it the live config. */
    void save(NerdBotConfig config) throws IOException;
}
