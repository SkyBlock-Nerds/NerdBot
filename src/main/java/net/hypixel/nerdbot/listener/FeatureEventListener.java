package net.hypixel.nerdbot.listener;

import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.feature.FeatureListener;
import org.jetbrains.annotations.NotNull;

public class FeatureEventListener implements EventListener {

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        NerdBotApp.getBot().getFeatures().stream().filter(botFeature -> botFeature instanceof FeatureListener).toList().forEach(botFeature -> {
            ((FeatureListener) botFeature).onEvent(event);
        });
    }

}
