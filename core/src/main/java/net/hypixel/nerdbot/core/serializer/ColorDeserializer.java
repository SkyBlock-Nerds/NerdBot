package net.hypixel.nerdbot.core.serializer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import java.awt.Color;
import java.lang.reflect.Type;

/**
 * Deserializes hex color strings into {@link Color} instances.
 */
public class ColorDeserializer implements JsonDeserializer<Color> {

    @Override
    public Color deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json == null || json.isJsonNull()) {
            return null;
        }

        String hex = json.getAsString();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }

        try {
            int rgb = (int) Long.parseLong(hex, 16);
            return new Color(rgb);
        } catch (NumberFormatException e) {
            throw new JsonParseException("Invalid color value: " + json.getAsString(), e);
        }
    }
}
