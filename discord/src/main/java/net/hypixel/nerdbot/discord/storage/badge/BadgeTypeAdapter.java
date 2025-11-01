package net.hypixel.nerdbot.discord.storage.badge;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class BadgeTypeAdapter implements JsonDeserializer<Badge> {

    @Override
    public Badge deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        String id = jsonObject.get("id").getAsString();
        String name = jsonObject.get("name").getAsString();
        JsonElement emojiId = jsonObject.get("emoji");

        if (jsonObject.has("tiers")) {
            JsonArray tiersArray = jsonObject.getAsJsonArray("tiers");
            List<TieredBadge.Tier> tiers = new ArrayList<>();

            for (JsonElement tierElement : tiersArray) {
                JsonObject tierObject = tierElement.getAsJsonObject();
                String tierName = tierObject.get("name").getAsString();
                String tierEmojiId = tierObject.get("emoji").getAsString();
                int tierValue = tierObject.get("tier").getAsInt();

                tiers.add(new TieredBadge.Tier(tierName, tierEmojiId, tierValue));
            }

            return new TieredBadge(id, name, tiers);
        } else {
            return new Badge(id, name, emojiId != null ? emojiId.getAsString() : null);
        }
    }
}