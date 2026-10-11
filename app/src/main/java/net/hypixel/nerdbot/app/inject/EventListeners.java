package net.hypixel.nerdbot.app.inject;

import jakarta.inject.Qualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Marks the set of objects registered with JDA as event listeners. */
@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface EventListeners {
}
