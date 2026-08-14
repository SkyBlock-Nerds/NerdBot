package net.hypixel.nerdbot.app.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overrides the config generator's heuristic example value for a field in the
 * generated example-config.json. The raw string is emitted as the example for
 * string fields; "true"/"false" are parsed for booleans.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExampleValue {

    String value();
}
