package net.hypixel.nerdbot.api.bot;

import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.feature.BotFeature;

import javax.security.auth.login.LoginException;
import java.util.List;

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