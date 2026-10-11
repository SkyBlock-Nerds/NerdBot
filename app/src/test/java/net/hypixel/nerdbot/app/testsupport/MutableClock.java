package net.hypixel.nerdbot.app.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A UTC clock that only moves when a test advances it. */
public final class MutableClock extends Clock {

    private Instant now;

    public MutableClock(Instant start) {
        this.now = start;
    }

    public MutableClock() {
        this(Instant.parse("2026-10-11T12:00:00Z"));
    }

    public void advance(Duration amount) {
        now = now.plus(amount);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
