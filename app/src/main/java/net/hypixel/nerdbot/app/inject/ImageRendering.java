package net.hypixel.nerdbot.app.inject;

import jakarta.inject.Qualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marks the pool generator images are rendered on. It has a fixed size so a burst of commands
 * waits its turn instead of holding every image in memory at once.
 */
@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface ImageRendering {
}
