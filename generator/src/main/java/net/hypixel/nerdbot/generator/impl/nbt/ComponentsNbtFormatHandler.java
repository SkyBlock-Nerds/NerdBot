package net.hypixel.nerdbot.generator.impl.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

public class ComponentsNbtFormatHandler implements NbtFormatHandler {

    @Override
    public boolean supports(JsonObject nbt) {
        return nbt != null && nbt.has("components");
    }

    @Override
    public NbtFormatMetadata extractMetadata(JsonObject nbt) {
        JsonElement componentsElement = nbt.get("components");
        if (componentsElement == null || !componentsElement.isJsonObject()) {
            return NbtFormatMetadata.EMPTY;
        }

        JsonObject components = componentsElement.getAsJsonObject();
        String skinValue = resolveSkinValue(components);
        Integer maxLineLength = resolveMaxLineLength(components);
        boolean enchanted = detectEnchanted(components);

        return NbtFormatMetadata.builder()
            .withValue(NbtFormatMetadata.KEY_PLAYER_HEAD_TEXTURE, skinValue)
            .withValue(NbtFormatMetadata.KEY_MAX_LINE_LENGTH, maxLineLength)
            .withValue(NbtFormatMetadata.KEY_ENCHANTED, enchanted ? Boolean.TRUE : null)
            .build();
    }

    private String resolveSkinValue(JsonObject components) {
        JsonElement profileElement = components.get("minecraft:profile");
        if (profileElement == null || !profileElement.isJsonObject()) {
            return null;
        }

        JsonObject profile = profileElement.getAsJsonObject();

        if (profile.has("properties")) {
            String texture = extractTextureFromProperties(profile.get("properties"));
            if (texture != null && !texture.isBlank()) {
                return texture;
            }
        }

        if (profile.has("textures")) {
            return extractTextureValue(profile.get("textures"));
        }

        return null;
    }

    private Integer resolveMaxLineLength(JsonObject components) {
        if (!components.has("minecraft:lore")) {
            return null;
        }

        JsonArray loreArray = components.getAsJsonArray("minecraft:lore");
        int maxLength = 0;

        for (JsonElement loreElement : loreArray) {
            if (!loreElement.isJsonObject()) {
                continue;
            }

            JsonObject loreEntry = loreElement.getAsJsonObject();
            String parsedLine = parseTextComponentForLength(loreEntry);
            maxLength = Math.max(maxLength, parsedLine.length());
        }

        return maxLength == 0 ? null : maxLength;
    }

    private boolean detectEnchanted(JsonObject components) {
        Boolean override = parseBooleanFromElement(components.get("minecraft:enchantment_glint_override"));
        if (override != null) {
            return override;
        }

        if (hasComponentEnchantments(components.get("minecraft:enchantments"))) {
            return true;
        }

        return hasComponentEnchantments(components.get("minecraft:stored_enchantments"));
    }

    private boolean hasComponentEnchantments(JsonElement element) {
        if (element == null) {
            return false;
        }

        if (element.isJsonArray()) {
            return !element.getAsJsonArray().isEmpty();
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("levels")) {
                JsonElement levels = obj.get("levels");
                if (levels.isJsonObject() && !levels.getAsJsonObject().entrySet().isEmpty()) {
                    return true;
                }
                if (levels.isJsonArray() && !levels.getAsJsonArray().isEmpty()) {
                    return true;
                }
            }

            return obj.has("entries") && obj.get("entries").isJsonArray() && !obj.getAsJsonArray("entries").isEmpty();
        }

        return false;
    }

    private Boolean parseBooleanFromElement(JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }

        if (element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }

        if (element.getAsJsonPrimitive().isNumber()) {
            return element.getAsNumber().intValue() != 0;
        }

        if (element.getAsJsonPrimitive().isString()) {
            String raw = element.getAsString().trim().toLowerCase(Locale.ROOT);
            if (raw.equals("true") || raw.equals("1") || raw.equals("1b")) {
                return true;
            }
            if (raw.equals("false") || raw.equals("0") || raw.equals("0b")) {
                return false;
            }
        }

        return null;
    }

    private String extractTextureFromProperties(JsonElement propertiesElement) {
        if (propertiesElement == null) {
            return null;
        }

        if (propertiesElement.isJsonArray()) {
            for (JsonElement propertyElement : propertiesElement.getAsJsonArray()) {
                String texture = extractTextureFromProperty(propertyElement);
                if (texture != null && !texture.isBlank()) {
                    return texture;
                }
            }
        } else if (propertiesElement.isJsonObject()) {
            JsonObject propertiesObject = propertiesElement.getAsJsonObject();
            if (propertiesObject.has("textures")) {
                return extractTextureValue(propertiesObject.get("textures"));
            }
        } else if (propertiesElement.isJsonPrimitive()) {
            return propertiesElement.getAsString();
        }

        return null;
    }

    private String extractTextureFromProperty(JsonElement propertyElement) {
        if (propertyElement == null || !propertyElement.isJsonObject()) {
            return null;
        }

        JsonObject property = propertyElement.getAsJsonObject();
        boolean texturesProperty = !property.has("name") || "textures".equalsIgnoreCase(property.get("name").getAsString());
        if (!texturesProperty) {
            return null;
        }

        if (property.has("value")) {
            return property.get("value").getAsString();
        }

        if (property.has("Value")) {
            return property.get("Value").getAsString();
        }

        if (property.has("textures")) {
            return extractTextureValue(property.get("textures"));
        }

        if (property.has("url")) {
            return property.get("url").getAsString();
        }

        return null;
    }

    private String extractTextureValue(JsonElement texturesElement) {
        if (texturesElement == null) {
            return null;
        }

        if (texturesElement.isJsonArray()) {
            for (JsonElement element : texturesElement.getAsJsonArray()) {
                String texture = extractTextureValue(element);
                if (texture != null && !texture.isBlank()) {
                    return texture;
                }
            }

            return null;
        }

        if (texturesElement.isJsonObject()) {
            JsonObject textureObject = texturesElement.getAsJsonObject();
            if (textureObject.has("value")) {
                return textureObject.get("value").getAsString();
            }

            if (textureObject.has("Value")) {
                return textureObject.get("Value").getAsString();
            }

            if (textureObject.has("url")) {
                return textureObject.get("url").getAsString();
            }

            if (textureObject.has("textures")) {
                String nested = extractTextureValue(textureObject.get("textures"));
                if (nested != null && !nested.isBlank()) {
                    return nested;
                }
            }
        } else if (texturesElement.isJsonPrimitive()) {
            return texturesElement.getAsString();
        }

        return null;
    }

    private String parseTextComponentForLength(JsonObject textComponent) {
        StringBuilder result = new StringBuilder();

        if (textComponent.has("text")) {
            String text = textComponent.get("text").getAsString();
            if (!text.isEmpty()) {
                result.append(text);
            }
        }

        if (textComponent.has("extra")) {
            JsonArray extraArray = textComponent.getAsJsonArray("extra");
            for (JsonElement extraElement : extraArray) {
                if (!extraElement.isJsonObject()) {
                    continue;
                }

                JsonObject extraComponent = extraElement.getAsJsonObject();
                if (extraComponent.has("text")) {
                    result.append(extraComponent.get("text").getAsString());
                }
            }
        }

        return result.toString();
    }
}