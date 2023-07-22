package net.hypixel.nerdbot.util.gson;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;

public abstract class TypeAdapter<T> implements JsonSerializer<T>, JsonDeserializer<T> {

}