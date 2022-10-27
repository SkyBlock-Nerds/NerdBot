package net.hypixel.nerdbot.listener;

import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;
import org.jetbrains.annotations.NotNull;

public class ShutdownListener {

    @SubscribeEvent
    public void onEvent(@NotNull ShutdownEvent event) {
        NerdBotApp.getBot().onEnd();
    }
}
