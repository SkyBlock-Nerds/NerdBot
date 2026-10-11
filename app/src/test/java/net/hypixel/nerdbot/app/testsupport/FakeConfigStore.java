package net.hypixel.nerdbot.app.testsupport;

import net.hypixel.nerdbot.app.config.ConfigStore;
import net.hypixel.nerdbot.app.config.NerdBotConfig;

import java.io.IOException;
import java.nio.file.Path;

/** Holds the live config in memory and counts saves; writes and reloads can be made to fail. */
public class FakeConfigStore implements ConfigStore {

    private NerdBotConfig current;
    private NerdBotConfig onDisk;
    private int saves;
    private int reloads;
    private boolean failWrites;
    private boolean failReloads;

    public FakeConfigStore(NerdBotConfig current) {
        this.current = current;
    }

    public NerdBotConfig current() {
        return current;
    }

    public int saves() {
        return saves;
    }

    public int reloads() {
        return reloads;
    }

    public void failWrites() {
        failWrites = true;
    }

    public void failReloads() {
        failReloads = true;
    }

    /** What the next reload reads from the "file". */
    public void fileContains(NerdBotConfig config) {
        this.onDisk = config;
    }

    @Override
    public Path file() {
        return Path.of("test.config.json");
    }

    @Override
    public void reload() {
        if (failReloads) {
            throw new IllegalStateException("Failed to parse config from test.config.json");
        }
        reloads++;
        if (onDisk != null) {
            current = onDisk;
        }
    }

    @Override
    public void save(NerdBotConfig config) throws IOException {
        if (failWrites) {
            throw new IOException("disk full");
        }
        saves++;
        current = config;
    }
}
