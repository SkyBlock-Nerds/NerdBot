package net.hypixel.nerdbot.api.bot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.hypixel.nerdbot.api.config.BotConfig;

import javax.security.auth.login.LoginException;

public interface Bot {

    void create(String[] args) throws LoginException;

    void configureMemoryUsage(JDABuilder builder);

    JDA getJDA();

    BotConfig getConfig();

    void onStart();

    void registerListeners();

    void onEnd();

    long getUptime();

}
