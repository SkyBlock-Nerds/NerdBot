package net.hypixel.nerdbot.discord.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.hypixel.nerdbot.core.gson.adapter.InstantTypeAdapter;
import net.hypixel.nerdbot.core.gson.adapter.UUIDTypeAdapter;
import net.hypixel.nerdbot.discord.storage.badge.Badge;
import net.hypixel.nerdbot.discord.storage.badge.BadgeTypeAdapter;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DataSerialization {

    public static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(UUID.class, new UUIDTypeAdapter())
        .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
        .registerTypeAdapter(Badge.class, new BadgeTypeAdapter())
        .create();
}
