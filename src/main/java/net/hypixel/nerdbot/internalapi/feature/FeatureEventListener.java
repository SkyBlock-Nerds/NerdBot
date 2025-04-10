package net.hypixel.nerdbot.internalapi.feature;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.NerdBotApp;

public class FeatureEventListener {

    @SubscribeEvent
    public void onEvent(GenericEvent event) {
        NerdBotApp.getBot().getFeatures().stream()
            .filter(FeatureListener.class::isInstance)
            .toList()
            .forEach(botFeature -> ((FeatureListener) botFeature).onEvent(event));
    }
}
