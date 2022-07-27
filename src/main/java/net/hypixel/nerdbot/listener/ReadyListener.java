package net.hypixel.nerdbot.listener;

import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;

public class ReadyListener {

    @SubscribeEvent
    public void onEvent(ReadyEvent event) {
        NerdBotApp.getBot().onStart();
    }

}
