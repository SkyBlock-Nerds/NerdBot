package net.hypixel.nerdbot.discord.util;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Reads secrets such as the bot token.
 * <p>
 * A secret named {@code bot.token} is read from the environment variable {@code BOT_TOKEN}, and
 * from the JVM system property {@code bot.token} when that variable is unset. Prefer the
 * environment variable: a {@code -D} flag is part of the command line, which every user on the
 * machine can read.
 */
@UtilityClass
public class Secrets {

    /**
     * Finds the secret with this name, or empty when it is unset or blank.
     */
    public static @NotNull Optional<String> find(@NotNull String name) {
        return find(name, System::getenv, System::getProperty);
    }

    static @NotNull Optional<String> find(@NotNull String name, @NotNull UnaryOperator<String> environment, @NotNull UnaryOperator<String> properties) {
        return present(environment.apply(environmentName(name)))
            .or(() -> present(properties.apply(name)));
    }

    /**
     * The environment variable holding the secret with this name: {@code bot.token} is
     * {@code BOT_TOKEN}.
     */
    public static @NotNull String environmentName(@NotNull String name) {
        return name.toUpperCase(Locale.ROOT).replace('.', '_');
    }

    private static Optional<String> present(String value) {
        return Optional.ofNullable(value).filter(found -> !found.isBlank());
    }
}
