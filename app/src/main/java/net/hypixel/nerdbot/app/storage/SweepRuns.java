package net.hypixel.nerdbot.app.storage;

import jakarta.inject.Qualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Marks the {@code sweep_runs} collection. */
@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface SweepRuns {
}
