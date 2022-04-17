package net.hypixel.nerdbot.util;

import java.util.concurrent.TimeUnit;

public class Util {

    public static void sleep(TimeUnit unit, long time) {
        try {
            Thread.sleep(unit.toMillis(time));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
