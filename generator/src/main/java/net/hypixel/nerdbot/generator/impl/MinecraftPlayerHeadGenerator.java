package net.hypixel.nerdbot.generator.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.skull.RenderedPlayerSkull;
import net.hypixel.nerdbot.core.HttpClient;
import net.hypixel.nerdbot.core.ImageUtil;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public class MinecraftPlayerHeadGenerator implements Generator {

    private static final Pattern TEXTURE_URL = Pattern.compile("(?:https?://textures.minecraft.net/texture/)?([a-zA-Z0-9]+)");
    private static final Pattern SKIN_BASE64_REGEX = Pattern.compile("^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?$");
    private static final Pattern HEX_TEXTURE_HASH = Pattern.compile("^[a-fA-F0-9]{64}$");

    private final String textureId;
    private final int scale;

    /**
     * Gets the Skin ID from a Base64 String
     *
     * @param base64SkinData Base64 Skin Data
     *
     * @return the skin ID
     */
    private static String base64ToSkinURL(String base64SkinData) {
        JsonObject skinData = JsonParser.parseString(new String(Base64.getDecoder().decode(base64SkinData))).getAsJsonObject();
        return skinData.get("textures").getAsJsonObject()
            .get("SKIN").getAsJsonObject()
            .get("url").getAsString()
            .replace("http://textures.minecraft.net/texture/", "");
    }

    @Override
    public @NotNull GeneratedObject render() {
        log.debug("Rendering player head for texture '{}' (scale={})", textureId, scale);

        BufferedImage headImage = createHead(textureId);
        log.debug("Rendered player head image (dimensions {}x{})", headImage.getWidth(), headImage.getHeight());
        return new GeneratedObject(headImage);
    }

    private BufferedImage createHead(String textureId) {
        // If no textureId provided, leave as null and let downstream logic handle

        if (isHexTextureHash(textureId)) {
            // Already a valid hex texture hash, use directly
        } else if (isSkinBase64(textureId)) {
            textureId = base64ToSkinURL(textureId);
        } else if (textureId.length() <= 16) {
            textureId = getPlayerHeadURL(textureId);
        } else {
            Matcher textureMatcher = TEXTURE_URL.matcher(textureId);
            if (textureMatcher.matches()) {
                textureId = textureMatcher.group(1);
            }
        }

        try {
            URL target = new URL("http://textures.minecraft.net/texture/" + textureId);
            BufferedImage skin = ImageIO.read(target);
            BufferedImage head = new RenderedPlayerSkull(skin).generate().getImage();

            if (scale > 0) {
                head = ImageUtil.upscaleImage(head, scale);
            } else if (scale < 0) {
                head = ImageUtil.downscaleImage(head, Math.abs(scale));
            }

            return head;
        } catch (MalformedURLException exception) {
            throw new GeneratorException("Malformed URL: `%s`", textureId);
        } catch (IOException exception) {
            throw new GeneratorException("Could not find skin with ID: `%s`", textureId);
        }
    }

    private String getPlayerHeadURL(String playerName) {
        playerName = playerName.replaceAll("[^a-zA-Z0-9_]", "");

        JsonObject userUUID;
        try {
            userUUID = HttpClient.getJson(String.format("https://api.mojang.com/users/profiles/minecraft/%s", playerName));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeneratorException("Could not find player with name: `%s`", playerName);
        }

        if (userUUID == null || userUUID.get("id") == null) {
            throw new GeneratorException("Could not find player with name: `%s`", playerName);
        }

        JsonObject userProfile;
        try {
            userProfile = HttpClient.getJson(String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s", userUUID.get("id").getAsString()));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeneratorException("Could not find player with name: `%s`", playerName);
        }

        if (userProfile == null || userProfile.get("properties") == null) {
            throw new GeneratorException("Could not find player with name: `%s`", playerName);
        }

        String base64SkinData = userProfile.get("properties").getAsJsonArray().get(0).getAsJsonObject().get("value").getAsString();
        return base64ToSkinURL(base64SkinData);
    }

    private boolean isHexTextureHash(String string) {
        return string != null && HEX_TEXTURE_HASH.matcher(string).matches();
    }

    private boolean isSkinBase64(String string) {
        if (string == null || string.length() <= 16) {
            return false;
        }

        if (!SKIN_BASE64_REGEX.matcher(string).matches()) {
            return false;
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(string);
        } catch (IllegalArgumentException e) {
            return false;
        }

        String json = new String(decoded, StandardCharsets.UTF_8);
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            return root != null && root.has("textures") && root.getAsJsonObject("textures").has("SKIN");
        } catch (JsonSyntaxException | IllegalStateException ex) {
            return false;
        }
    }

    public static class Builder implements ClassBuilder<MinecraftPlayerHeadGenerator> {
        private String texture;
        private int scale;

        public Builder withSkin(String texture) {
            this.texture = texture;
            return this;
        }

        public Builder withScale(int scale) {
            this.scale = scale;
            return this;
        }

        @Override
        public MinecraftPlayerHeadGenerator build() {
            return new MinecraftPlayerHeadGenerator(texture, scale);
        }
    }
}
