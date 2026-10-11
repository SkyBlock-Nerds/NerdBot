package net.hypixel.nerdbot.discord.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value to show for a field in the generated example-config.json, for fields with no default
 * where the generator's guess would be invalid (a pack id, a URL template, a time zone). It's
 * converted to the field's type: strings, booleans, numbers and enums, and a list or set of
 * strings gets it as its only element.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExampleValue {

    String value();
}
