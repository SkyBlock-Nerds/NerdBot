package net.hypixel.nerdbot.app.inject;

import jakarta.inject.Qualifier;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Marks the set of objects whose annotated methods are registered as slash commands. */
@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface SlashCommandHandlers {
}
