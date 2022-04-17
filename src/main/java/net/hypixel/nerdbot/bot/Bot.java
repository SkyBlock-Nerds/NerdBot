package net.hypixel.nerdbot.bot;

import net.dv8tion.jda.api.JDABuilder;

import javax.security.auth.login.LoginException;

public interface Bot {

    void create(String[] args) throws LoginException;

    void configureMemoryUsage(JDABuilder builder);

    void onStart();

    void registerListeners();

    void onEnd();

}
