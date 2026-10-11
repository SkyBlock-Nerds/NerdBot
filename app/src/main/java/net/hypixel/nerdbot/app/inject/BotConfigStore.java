package net.hypixel.nerdbot.app.inject;

import jakarta.inject.Inject;
import net.hypixel.nerdbot.app.config.ConfigStore;
import net.hypixel.nerdbot.app.config.NerdBotConfig;

import java.io.IOException;
import java.nio.file.Path;

public class BotConfigStore implements ConfigStore {

    private final BotState state;

    @Inject
    public BotConfigStore(BotState state) {
        this.state = state;
    }

    @Override
    public Path file() {
        return state.configFile();
    }

    @Override
    public void reload() {
        state.loadConfig();
    }

    @Override
    public void save(NerdBotConfig config) throws IOException {
        state.saveConfig(config);
    }
}
