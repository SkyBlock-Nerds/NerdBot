package net.hypixel.nerdbot.discord.api.bot;

import java.util.List;
import javax.security.auth.login.LoginException;
import net.hypixel.nerdbot.discord.storage.database.Database;
import net.hypixel.nerdbot.discord.api.feature.BotFeature;

public interface Bot {

    void create(String[] args) throws LoginException;

    void loadConfig();

    Database getDatabase();

    void onStart();

    List<BotFeature> getFeatures();

    void onEnd();

    long getUptime();

    boolean isReadOnly();
}
