package net.hypixel.nerdbot.bot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.hypixel.nerdbot.config.BotConfig;

import javax.security.auth.login.LoginException;

public interface Bot {

    void create(String[] args) throws LoginException;

    void configureMemoryUsage(JDABuilder builder);

    JDA getJDA();

    void onStart();

    void registerListeners();

    void onEnd();

    BotConfig getConfig();

}
