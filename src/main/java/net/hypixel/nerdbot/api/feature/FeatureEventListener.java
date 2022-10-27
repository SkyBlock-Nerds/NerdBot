package net.hypixel.nerdbot.api.feature;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;
import org.jetbrains.annotations.NotNull;

public class FeatureEventListener {

    @SubscribeEvent
    public void onEvent(GenericEvent event) {
        NerdBotApp.getBot().getFeatures().stream().filter(botFeature -> botFeature instanceof FeatureListener).toList().forEach(botFeature -> {
            ((FeatureListener) botFeature).onEvent(event);
        });
    }
}
