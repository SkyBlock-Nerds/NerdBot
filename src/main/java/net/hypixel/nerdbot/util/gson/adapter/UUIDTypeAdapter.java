package net.hypixel.nerdbot.util.gson.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import net.hypixel.nerdbot.util.Util;

import java.io.IOException;
import java.util.UUID;

public class UUIDTypeAdapter extends TypeAdapter<UUID> {

    @Override
    public void write(JsonWriter jsonWriter, UUID uuid) throws IOException {
        if (uuid == null) {
            jsonWriter.nullValue();
            return;
        }

        jsonWriter.value(uuid.toString());
    }

    @Override
    public UUID read(JsonReader jsonReader) throws IOException {
        return Util.toUUID(jsonReader.nextString());
    }
}