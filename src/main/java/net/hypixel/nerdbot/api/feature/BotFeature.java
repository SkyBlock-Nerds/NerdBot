package net.hypixel.nerdbot.api.feature;

import net.hypixel.nerdbot.NerdBotApp;

public abstract class BotFeature {

    public void onStart() {
        NerdBotApp.getBot().getJDA().addEventListener(this);
    }

    public abstract void onEnd();

}
