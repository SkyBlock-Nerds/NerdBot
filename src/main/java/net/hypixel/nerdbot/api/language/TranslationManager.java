package net.hypixel.nerdbot.api.language;

import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.language.UserLanguage;
import net.hypixel.nerdbot.util.JsonUtils;
import net.hypixel.nerdbot.util.exception.TranslationException;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Log4j2
public class TranslationManager {

    private static final UserLanguage DEFAULT_LANGUAGE = UserLanguage.ENGLISH;
    private static final String ERROR_MESSAGE = "Translation not found: %s";
    private static final Map<UserLanguage, JsonObject> TRANSLATION_CACHE = new HashMap<>();

    private TranslationManager() {
    }

    /**
     * Load the language file based on the {@link UserLanguage} file name
     *
     * @param language The {@link UserLanguage} to load translations for
     *
     * @return A {@link JsonObject} containing the file contents
     */
    @SneakyThrows
    private static JsonObject loadTranslations(UserLanguage language) {
        if (TRANSLATION_CACHE.containsKey(language)) {
            return TRANSLATION_CACHE.get(language);
        }

        try (InputStream stream = TranslationManager.class.getClassLoader().getResourceAsStream("languages/" + language.getFileName())) {
            if (stream == null) {
                throw new RuntimeException("Failed to load translations for language " + language.name() + " from " + language.getFileName());
            }

            String contents = new String(stream.readAllBytes());
            JsonObject jsonObject = NerdBotApp.GSON.fromJson(contents, JsonObject.class);
            TRANSLATION_CACHE.put(language, jsonObject);
            log.info("Loaded translations for language " + language.name() + " from " + language.getFileName());
            return jsonObject;
        } catch (Exception e) {
            throw new TranslationException("Failed to load translations for language " + language.name(), e);
        }
    }

    /**
     * Reload translations for the given {@link UserLanguage}
     *
     * @param language The {@link UserLanguage} to reload translations for
     */
    public static void reloadTranslations(UserLanguage language) {
        TRANSLATION_CACHE.remove(language);
        loadTranslations(language);
    }

    /**
     * Translate a given key for the provided {@link DiscordUser} based on their chosen {@link UserLanguage}
     *
     * @param discordUser The {@link DiscordUser} to take the {@link UserLanguage} from
     * @param key         The key of the translation
     * @param args        Optional arguments to replace variables in the translation
     *
     * @return A string with the translated key based on the {@link DiscordUser}'s {@link UserLanguage} and any optional arguments
     */
    public static String translate(@Nullable DiscordUser discordUser, String key, Object... args) {
        UserLanguage language = discordUser != null ? discordUser.getLanguage() : DEFAULT_LANGUAGE;
        JsonObject jsonObject = loadTranslations(language);

        if (jsonObject == null) {
            return ERROR_MESSAGE.formatted(key);
        }

        JsonElement element = jsonObject;
        for (String k : key.split("\\.")) {
            if (k.contains("[")) {
                String[] split = k.split("\\[");
                String property = split[0];
                String indexStr = split[1].replace("]", "");
                element = JsonUtils.getIndexedElement(element, property, indexStr);
            } else {
                element = JsonUtils.getNextElement(element, k);
            }

            if (element == null) {
                if (language != DEFAULT_LANGUAGE) {
                    return translate(key, args);
                }

                return ERROR_MESSAGE.formatted(key);
            }
        }

        if (!element.isJsonPrimitive()) {
            return ERROR_MESSAGE.formatted(key);
        }

        String translation = element.getAsString();

        if (args.length > 0) {
            translation = String.format(translation, args);
        }

        return translation;
    }

    /**
     * Translate a key into the default {@link UserLanguage}
     *
     * @param key  The key of the translation
     * @param args Optional arguments to replace variables in the translation
     *
     * @return A translated string using the default {@link UserLanguage} of the given key and optional arguments
     */
    public static String translate(String key, Object... args) {
        return translate(null, key, args);
    }

    /**
     * Reply to a {@link GuildSlashEvent} with a translated key and given {@link DiscordUser}
     *
     * @param event       The {@link GuildSlashEvent} to reply to
     * @param discordUser The {@link DiscordUser} to take the {@link UserLanguage} from
     * @param key         The key of the translation
     * @param args        Optional arguments to replace variables in the translation
     */
    public static void reply(GuildSlashEvent event, DiscordUser discordUser, String key, Object... args) {
        event.reply(translate(discordUser, key, args)).queue();
    }

    /**
     * Reply to a {@link GuildSlashEvent} without a given {@link DiscordUser}. Will translate to the default {@link UserLanguage}
     *
     * @param event The {@link GuildSlashEvent} to reply to
     * @param key   The key of the translation
     * @param args  Optional arguments to replace variables in the translation
     */
    public static void reply(GuildSlashEvent event, String key, Object... args) {
        event.reply(translate(key, args)).queue();
    }

    /**
     * Edit a message with a given translation based on the {@link UserLanguage} of a {@link DiscordUser}
     *
     * @param hook        The {@link InteractionHook} to edit the message of
     * @param discordUser The {@link DiscordUser} to take the {@link UserLanguage} from
     * @param key         The key of the translation
     * @param args        Optional arguments to replace variables in the translation
     */
    public static void edit(InteractionHook hook, DiscordUser discordUser, String key, Object... args) {
        hook.editOriginal(translate(discordUser, key, args)).queue();
    }

    /**
     * Edit a message with a given translation based on the default {@link UserLanguage}
     *
     * @param hook The {@link InteractionHook} to edit the message of
     * @param key  The key of the translation
     * @param args Optional arguments to replace variables in the translation
     */
    public static void edit(InteractionHook hook, String key, Object... args) {
        hook.editOriginal(translate(key, args)).queue();
    }

    /**
     * Send a message in a {@link GuildMessageChannel} with a translation based on the {@link UserLanguage} of a {@link DiscordUser}
     *
     * @param channel     The {@link GuildMessageChannel} to send the translated message in
     * @param discordUser The {@link DiscordUser} to take the {@link UserLanguage} from
     * @param key         The key of the translation
     * @param args        Optional arguments to replace variables in the translation
     */
    public static void send(GuildMessageChannel channel, DiscordUser discordUser, String key, Object... args) {
        channel.sendMessage(translate(discordUser, key, args)).queue();
    }

    /**
     * @param hook        The {@link InteractionHook} to edit the message of
     * @param discordUser The {@link DiscordUser} to take the {@link UserLanguage} from
     * @param key         The key of the translation
     * @param args        Optional arguments to replace variables in the translation
     */
    public static void send(InteractionHook hook, DiscordUser discordUser, String key, Object... args) {
        hook.sendMessage(translate(discordUser, key, args)).queue();
    }
}
