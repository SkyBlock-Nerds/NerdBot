package net.hypixel.nerdbot.api.feature;

import java.util.Timer;

public abstract class BotFeature {

    protected final Timer timer = new Timer();

    public abstract void onStart();

    public abstract void onEnd();

}
