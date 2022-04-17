package net.hypixel.nerdbot.listener;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.hypixel.nerdbot.NerdBotApp;
import org.jetbrains.annotations.NotNull;

public class ShutdownListener implements EventListener {

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        if (event instanceof ShutdownEvent) {
            NerdBotApp.getBot().onEnd();
        }
    }
}
