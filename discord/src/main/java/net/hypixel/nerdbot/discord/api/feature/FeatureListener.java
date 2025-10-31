package net.hypixel.nerdbot.discord.api.feature;

import net.dv8tion.jda.api.events.GenericEvent;

public interface FeatureListener {

    void onEvent(GenericEvent event);

}