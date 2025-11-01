package net.hypixel.nerdbot.discord.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.hypixel.nerdbot.core.json.adapter.InstantTypeAdapter;
import net.hypixel.nerdbot.core.json.adapter.UUIDTypeAdapter;
import net.hypixel.nerdbot.discord.storage.badge.Badge;
import net.hypixel.nerdbot.discord.storage.badge.BadgeTypeAdapter;

import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DataSerialization {

    public static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
        .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
        .registerTypeAdapter(Badge.class, new BadgeTypeAdapter())
        .create();
}
