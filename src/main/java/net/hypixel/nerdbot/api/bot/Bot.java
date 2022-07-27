package net.hypixel.nerdbot.api.bot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.hypixel.nerdbot.api.config.BotConfig;
import net.hypixel.nerdbot.api.feature.BotFeature;

import javax.security.auth.login.LoginException;
import java.util.List;

public interface Bot {

    void create(String[] args) throws LoginException;

    void configureMemoryUsage(JDABuilder builder);

    JDA getJDA();

    BotConfig getConfig();

    void onStart();

    List<BotFeature> getFeatures();

    void onEnd();

    long getUptime();

}
