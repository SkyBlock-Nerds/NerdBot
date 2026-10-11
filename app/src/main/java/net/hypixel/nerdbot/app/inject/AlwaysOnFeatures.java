package net.hypixel.nerdbot.app.inject;

import jakarta.inject.Qualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Marks the features that run whatever the config says, such as the repository autosave. */
@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface AlwaysOnFeatures {
}
