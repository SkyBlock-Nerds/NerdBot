package net.hypixel.nerdbot.core.json.adapter;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;

public final class InstantTypeAdapter extends TypeAdapter<Instant> {

    @Override
    public void write(JsonWriter jsonWriter, Instant instant) throws IOException {
        jsonWriter.value(instant.toEpochMilli());
    }

    @Override
    public Instant read(JsonReader jsonReader) throws IOException {
        return Instant.ofEpochMilli(jsonReader.nextLong());
    }
}