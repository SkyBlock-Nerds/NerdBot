package net.hypixel.nerdbot.internalapi.feature;

import net.dv8tion.jda.api.events.GenericEvent;

public interface FeatureListener {

    void onEvent(GenericEvent event);

}
