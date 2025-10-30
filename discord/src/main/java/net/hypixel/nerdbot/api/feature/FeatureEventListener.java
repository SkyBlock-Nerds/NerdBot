package net.hypixel.nerdbot.api.feature;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import net.hypixel.nerdbot.BotEnvironment;

public class FeatureEventListener {

    @SubscribeEvent
    public void onEvent(GenericEvent event) {
        BotEnvironment.getBot().getFeatures().stream()
            .filter(FeatureListener.class::isInstance)
            .toList()
            .forEach(botFeature -> ((FeatureListener) botFeature).onEvent(event));
    }
}