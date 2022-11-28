package net.hypixel.nerdbot.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class DelayedObject implements Delayed {

    private final Object object;
    private final long expiresTime;

    public DelayedObject(Object object, long delay) {
        this.object = object;
        this.expiresTime = System.currentTimeMillis() + delay;
    }

    @Override
    public final int compareTo(@NotNull Delayed o) {
        long diff = getDelay(TimeUnit.MILLISECONDS) - o.getDelay(TimeUnit.MILLISECONDS);
        diff = Math.min(diff, 1);
        diff = Math.max(diff, -1);
        return (int) diff;
    }

    @Override
    public final long getDelay(@NotNull TimeUnit unit) {
        long delay = expiresTime - System.currentTimeMillis();
        return unit.convert(delay, TimeUnit.MILLISECONDS);
    }

    public Object getObject() {
        return object;
    }
}
