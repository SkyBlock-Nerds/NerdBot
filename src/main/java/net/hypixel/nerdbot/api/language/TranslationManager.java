package net.hypixel.nerdbot.api.language;

import com.freya02.botcommands.api.application.slash.GuildSlashEvent;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.UserLanguage;
import net.hypixel.nerdbot.util.JsonUtil;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class TranslationManager {

    private static final UserLanguage DEFAULT_LANGUAGE = UserLanguage.ENGLISH;
    private static final String ERROR_MESSAGE = "Translation not found";
    private static final Map<UserLanguage, JsonObject> TRANSLATION_CACHE = new HashMap<>();
    private static final TranslationManager INSTANCE = new TranslationManager();

    private TranslationManager() {
    }

    public static TranslationManager getInstance() {
        return INSTANCE;
    }

    private JsonObject loadTranslations(UserLanguage language) {
        if (TRANSLATION_CACHE.containsKey(language)) {
            return TRANSLATION_CACHE.get(language);
        }

        try {
            Path path = Paths.get("./src/main/resources/languages/" + language.getFileName());
            String content = new String(Files.readAllBytes(path));
            JsonObject jsonObject = NerdBotApp.GSON.fromJson(content, JsonObject.class);
            TRANSLATION_CACHE.put(language, jsonObject);
            return jsonObject;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void reloadTranslations(UserLanguage language) {
        TRANSLATION_CACHE.remove(language);
        loadTranslations(language);
    }

    public String translate(@Nullable DiscordUser user, String key, Object... args) {
        UserLanguage language = user != null ? user.getLanguage() : DEFAULT_LANGUAGE;
        JsonObject jsonObject = loadTranslations(language);

        if (jsonObject == null) {
            return ERROR_MESSAGE;
        }

        JsonElement element = jsonObject;
        for (String k : key.split("\\.")) {
            if (k.contains("[")) {
                String[] split = k.split("\\[");
                String property = split[0];
                String indexStr = split[1].replace("]", "");
                element = JsonUtil.getIndexedElement(element, property, indexStr);
            } else {
                element = JsonUtil.getNextElement(element, k);
            }

            if (element == null) {
                return ERROR_MESSAGE;
            }
        }

        if (!element.isJsonPrimitive()) {
            return ERROR_MESSAGE;
        }

        String translation = element.getAsString();
        if (args.length > 0) {
            translation = String.format(translation, args);
        }

        return translation;
    }

    public String translate(String key, Object... args) {
        return translate(null, key, args);
    }

    public void reply(GuildSlashEvent event, DiscordUser discordUser, String key, Object... args) {
        event.reply(translate(discordUser, key, args)).queue();
    }

    public void edit(InteractionHook hook, DiscordUser discordUser, String key, Object... args) {
        hook.editOriginal(translate(discordUser, key, args)).queue();
    }

    public void reply(GuildSlashEvent event, String key, Object... args) {
        event.reply(translate(key, args)).queue();
    }

    public void edit(InteractionHook hook, String key, Object... args) {
        hook.editOriginal(translate(key, args)).queue();
    }

    public void send(MessageChannel channel, DiscordUser discordUser, String key, Object... args) {
        channel.sendMessage(translate(discordUser, key, args)).queue();
    }
}
