package net.hypixel.nerdbot.discord.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Leaves an optional field at its default in the generated example-config.json, for fields where
 * any invented value would be wrong (e.g. a reference to another pack that doesn't exist).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NoExampleValue {
}
