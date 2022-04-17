package net.hypixel.nerdbot.listener;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.hypixel.nerdbot.NerdBotApp;
import org.jetbrains.annotations.NotNull;

public class ReadyListener implements EventListener {

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (event instanceof ReadyEvent) {
            NerdBotApp.getBot().onStart();
        }
    }
}
