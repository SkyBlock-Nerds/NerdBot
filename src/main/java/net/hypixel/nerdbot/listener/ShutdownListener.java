package net.hypixel.nerdbot.listener;

import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;

public class ShutdownListener {

    @SubscribeEvent
    public void onEvent(ShutdownEvent event) {
        NerdBotApp.getBot().onEnd();
    }

}
