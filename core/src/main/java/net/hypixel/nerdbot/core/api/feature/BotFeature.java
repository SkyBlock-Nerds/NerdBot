package net.hypixel.nerdbot.core.api.feature;

import java.util.Timer;

public abstract class BotFeature {

    protected final Timer timer = new Timer();

    public abstract void onFeatureStart();

    public abstract void onFeatureEnd();

}