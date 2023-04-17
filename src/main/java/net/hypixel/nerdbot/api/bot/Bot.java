package net.hypixel.nerdbot.api.bot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.hypixel.nerdbot.api.database.Database;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.bot.config.BotConfig;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.util.List;

public interface Bot {

    void create(String[] args) throws LoginException;

    void configureMemoryUsage(JDABuilder builder);

    JDA getJDA();

    void loadConfig();

    BotConfig getConfig();

    Database getDatabase();

    void onStart();

    List<BotFeature> getFeatures();

    void onEnd();

    long getUptime();

    boolean isReadOnly();

    boolean writeConfig(@NotNull BotConfig newConfig);

}
