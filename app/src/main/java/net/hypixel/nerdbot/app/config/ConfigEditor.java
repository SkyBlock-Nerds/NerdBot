package net.hypixel.nerdbot.app.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import net.hypixel.nerdbot.marmalade.json.DataSerialization;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Changes one value in the live config by its dotted path, such as {@code roleConfig.limboRoleId}
 * or {@code features[2].enabled}. The edit is only saved when the whole config still loads, so a
 * typo can't stop the bot from starting.
 */
@Singleton
public class ConfigEditor {

    private static final Set<String> BLOCKED_KEYS = Set.of("ownerIds", "guildId", "token", "databaseUri", "databaseName");
    private static final Pattern INDEXED_KEY = Pattern.compile("(.+)\\[(\\d+)]");
    // Nulls are written out so that settings which are unset can still be found by their path
    private static final Gson TREE_GSON = DataSerialization.GSON.newBuilder().serializeNulls().create();

    public sealed interface Result {
    }

    public record Saved(NerdBotConfig config) implements Result {
    }

    /** {@code reason} is safe to show the admin. */
    public record Rejected(String reason) implements Result {
    }

    private final Provider<NerdBotConfig> config;
    private final ConfigStore store;

    @Inject
    public ConfigEditor(Provider<NerdBotConfig> config, ConfigStore store) {
        this.config = config;
        this.store = store;
    }

    static String rootKey(String key) {
        return key.contains(".") ? key.substring(0, key.indexOf('.')) : key;
    }

    static boolean isBlocked(String key) {
        Matcher indexed = INDEXED_KEY.matcher(rootKey(key));
        return BLOCKED_KEYS.contains(indexed.matches() ? indexed.group(1) : rootKey(key));
    }

    public synchronized Result edit(String key, String rawValue) throws IOException {
        if (isBlocked(key)) {
            return new Rejected("Editing the `" + rootKey(key) + "` config key is not permitted.");
        }

        JsonElement value;
        try {
            value = JsonParser.parseString(rawValue);
        } catch (JsonParseException exception) {
            return new Rejected("`" + rawValue + "` isn't valid JSON. Put text in quotes, for example \"hello world\".");
        }

        JsonObject root = TREE_GSON.toJsonTree(config.get()).getAsJsonObject();
        String problem = set(root, key, value);
        if (problem != null) {
            return new Rejected(problem);
        }

        NerdBotConfig edited;
        try {
            edited = DataSerialization.GSON.fromJson(root, NerdBotConfig.class);
        } catch (JsonParseException | IllegalStateException | NumberFormatException exception) {
            return new Rejected("`" + rawValue + "` doesn't fit `" + key + "`: " + exception.getMessage());
        }

        store.save(edited);
        return new Saved(edited);
    }

    /** @return why the path can't be set, or null once it has been */
    private static String set(JsonObject root, String key, JsonElement value) {
        String[] segments = key.split("\\.", -1);
        JsonElement current = root;

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            boolean last = i == segments.length - 1;
            Matcher indexed = INDEXED_KEY.matcher(segment);
            String name = indexed.matches() ? indexed.group(1) : segment;

            if (!current.isJsonObject() || !current.getAsJsonObject().has(name)) {
                return "There's no `" + String.join(".", Arrays.copyOfRange(segments, 0, i + 1)) + "` in the config.";
            }

            JsonObject parent = current.getAsJsonObject();
            if (!indexed.matches()) {
                if (last) {
                    parent.add(name, value);
                    return null;
                }
                current = parent.get(name);
                continue;
            }

            if (!parent.get(name).isJsonArray()) {
                return "`" + name + "` isn't a list, so it can't take an index.";
            }

            JsonArray array = parent.getAsJsonArray(name);
            int index = parseIndex(indexed.group(2));
            if (index < 0 || index >= array.size()) {
                return "`" + name + "` only has " + array.size() + " entries.";
            }

            if (last) {
                array.set(index, value);
                return null;
            }
            current = array.get(index);
        }

        return "There's nothing to edit at `" + key + "`.";
    }

    private static int parseIndex(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }
}
