package net.hypixel.nerdbot.util.gson;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import net.hypixel.nerdbot.util.Util;

import java.lang.reflect.Type;
import java.util.UUID;

public class UUIDTypeAdapter extends TypeAdapter<UUID> {

    @Override
    public UUID deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException {
        return Util.toUUID(json.getAsString());
    }

    @Override
    public JsonElement serialize(UUID src, Type srcType, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
    }

}
