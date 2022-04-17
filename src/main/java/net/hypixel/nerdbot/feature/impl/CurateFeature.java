package net.hypixel.nerdbot.feature.impl;

import net.hypixel.nerdbot.feature.BotFeature;
import net.hypixel.nerdbot.util.Logger;

public class CurateFeature extends BotFeature {

    @Override
    public void onStart() {
        Logger.info("Curation ready!");
    }

    @Override
    public void onEnd() {

    }
}
