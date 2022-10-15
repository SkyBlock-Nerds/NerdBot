package net.hypixel.nerdbot.api.bot;

import net.aerh.jdacommands.CommandManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.hypixel.nerdbot.api.feature.BotFeature;
import net.hypixel.nerdbot.bot.config.BotConfig;

import javax.security.auth.login.LoginException;
import java.util.List;

public interface Bot {

    void create(String[] args) throws LoginException;

    void configureMemoryUsage(JDABuilder builder);

    JDA getJDA();

    void loadConfig();

    BotConfig getConfig();

    void onStart();

    List<BotFeature> getFeatures();

    CommandManager getCommands();

    void onEnd();

    long getUptime();

    boolean isReadOnly();
}
