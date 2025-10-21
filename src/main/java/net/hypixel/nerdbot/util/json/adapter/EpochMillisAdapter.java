package net.hypixel.nerdbot.util.json.adapter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.TimeZone;

public class EpochMillisAdapter implements JsonSerializer<Long>, JsonDeserializer<Long> {

    private static final String[] LEGACY_PATTERNS = new String[]{
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "MMM d, yyyy, h:mm:ss a",
        "MMM d, yyyy, h:mm:ss a z"
    };

    @Override
    public Long deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json == null || json.isJsonNull()) {
            return null;
        }

        return parseElement(json);
    }

    @Override
    public JsonElement serialize(Long src, Type typeOfSrc, JsonSerializationContext context) {
        return src == null ? JsonNull.INSTANCE : new JsonPrimitive(src);
    }

    private long parseElement(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return parsePrimitive(element.getAsJsonPrimitive());
        }

        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();

            if (object.has("$numberLong")) {
                return parseElement(object.get("$numberLong"));
            }

            if (object.has("$date")) {
                return parseElement(object.get("$date"));
            }
        }

        throw new JsonParseException("Unsupported date representation: " + element);
    }

    private long parsePrimitive(JsonPrimitive primitive) {
        if (primitive.isNumber()) {
            return primitive.getAsLong();
        }

        if (primitive.isString()) {
            String value = primitive.getAsString();

            if (value.isEmpty()) {
                throw new JsonParseException("Cannot parse empty date string");
            }

            // Try direct numeric parse
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
            }

            // Try ISO-8601
            try {
                return Instant.parse(value).toEpochMilli();
            } catch (DateTimeParseException ignored) {
            }

            // Try legacy patterns
            for (String pattern : LEGACY_PATTERNS) {
                try {
                    SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                    if (pattern.contains("'Z'") || pattern.contains("X")) {
                        format.setTimeZone(TimeZone.getTimeZone("UTC"));
                    }
                    return format.parse(value).getTime();
                } catch (ParseException ignored) {
                }
            }
        }

        throw new JsonParseException("Unsupported date primitive: " + primitive);
    }
}
