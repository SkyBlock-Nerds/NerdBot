package net.hypixel.nerdbot.generator.impl.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.generator.exception.TooManyTexturesException;

public class LegacyNbtFormatHandler implements NbtFormatHandler {

    @Override
    public boolean supports(JsonObject nbt) {
        return nbt != null && nbt.has("tag");
    }

    @Override
    public NbtFormatMetadata extractMetadata(JsonObject nbt) {
        JsonObject tagObject = nbt.getAsJsonObject("tag");
        if (tagObject == null) {
            return NbtFormatMetadata.EMPTY;
        }

        String skinValue = resolveSkinValue(tagObject);
        Integer maxLineLength = resolveMaxLineLength(tagObject);
        boolean enchanted = detectEnchanted(tagObject);

        return NbtFormatMetadata.builder()
            .withValue(NbtFormatMetadata.KEY_PLAYER_HEAD_TEXTURE, skinValue)
            .withValue(NbtFormatMetadata.KEY_MAX_LINE_LENGTH, maxLineLength)
            .withValue(NbtFormatMetadata.KEY_ENCHANTED, enchanted ? Boolean.TRUE : null)
            .build();
    }

    private String resolveSkinValue(JsonObject tagObject) {
        if (!tagObject.has("SkullOwner")) {
            return null;
        }

        JsonObject skullOwner = tagObject.getAsJsonObject("SkullOwner");
        if (skullOwner == null || !skullOwner.has("Properties")) {
            return null;
        }

        JsonObject properties = skullOwner.getAsJsonObject("Properties");
        if (properties == null || !properties.has("textures")) {
            return null;
        }

        JsonArray textures = properties.getAsJsonArray("textures");
        if (textures == null || textures.isEmpty()) {
            return null;
        }

        if (textures.size() > 1) {
            throw new TooManyTexturesException();
        }

        JsonObject texture = textures.get(0).getAsJsonObject();
        if (texture.has("Value")) {
            return texture.get("Value").getAsString();
        }

        if (texture.has("value")) {
            return texture.get("value").getAsString();
        }

        return null;
    }

    private Integer resolveMaxLineLength(JsonObject tag) {
        if (!tag.has("display")) {
            return null;
        }

        JsonObject display = tag.getAsJsonObject("display");
        if (display == null || !display.has("Lore")) {
            return null;
        }

        JsonArray loreArray = display.getAsJsonArray("Lore");
        if (loreArray == null || loreArray.isEmpty()) {
            return null;
        }

        int maxLength = 0;
        for (JsonElement loreElement : loreArray) {
            if (!loreElement.isJsonPrimitive()) {
                continue;
            }

            String line = loreElement.getAsString();
            maxLength = Math.max(maxLength, line.length());
        }

        return maxLength == 0 ? null : maxLength;
    }

    private boolean detectEnchanted(JsonObject tag) {
        if (tag == null) {
            return false;
        }

        JsonElement[] legacyKeys = {
            tag.get("Enchantments"),
            tag.get("StoredEnchantments"),
            tag.get("ench")
        };

        for (JsonElement element : legacyKeys) {
            if (element == null) {
                continue;
            }

            if (element.isJsonArray() && !element.getAsJsonArray().isEmpty()) {
                return true;
            }

            if (element.isJsonObject() && !element.getAsJsonObject().entrySet().isEmpty()) {
                return true;
            }
        }

        return false;
    }

}