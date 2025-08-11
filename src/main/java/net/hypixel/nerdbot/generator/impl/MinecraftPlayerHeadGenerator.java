package net.hypixel.nerdbot.generator.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.skull.RenderedPlayerSkull;
import net.hypixel.nerdbot.util.HttpUtils;
import net.hypixel.nerdbot.util.ImageUtil;
import net.hypixel.nerdbot.util.StringUtils;

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
public class MinecraftPlayerHeadGenerator implements Generator {

    private static final String DEFAULT_SKIN_VALUE = "31f477eb1a7beee631c2ca64d06f8f68fa93a3386d04452ab27f43acdf1b60cb"; // TODO find better value
    private static final Pattern TEXTURE_URL = Pattern.compile("(?:https?://textures.minecraft.net/texture/)?([a-zA-Z0-9]+)");

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
        JsonObject skinData = NerdBotApp.GSON.fromJson(new String(Base64.getDecoder().decode(base64SkinData)), JsonObject.class);
        return skinData.get("textures").getAsJsonObject()
            .get("SKIN").getAsJsonObject()
            .get("url").getAsString()
            .replace("http://textures.minecraft.net/texture/", "");
    }

    @Override
    public GeneratedObject generate() {
        return new GeneratedObject(createHead(textureId));
    }

    private BufferedImage createHead(String textureId) {
        if (textureId == null) {
            textureId = DEFAULT_SKIN_VALUE;
        }

        if (isSkinBase64(textureId)) {
            textureId = base64ToSkinURL(textureId);
        } else if (textureId.length() <= 16) {
            textureId = getPlayerHeadURL(textureId);
        }

        Matcher textureMatcher = TEXTURE_URL.matcher(textureId);
        if (textureMatcher.matches()) {
            textureId = textureMatcher.group(1);
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
            userUUID = HttpUtils.makeHttpRequest(String.format("https://api.mojang.com/users/profiles/minecraft/%s", playerName));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeneratorException("Could not find player with name: `%s`", playerName);
        }

        if (userUUID == null || userUUID.get("id") == null) {
            throw new GeneratorException("Could not find player with name: `%s`", playerName);
        }

        JsonObject userProfile;
        try {
            userProfile = HttpUtils.makeHttpRequest(String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s", userUUID.get("id").getAsString()));
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

    private boolean isSkinBase64(String string) {
        if (string == null || string.length() <= 16) {
            return false;
        }

        if (!StringUtils.SKIN_BASE64_REGEX.matcher(string).matches()) {
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
            JsonObject root = NerdBotApp.GSON.fromJson(json, JsonObject.class);
            return root != null && root.has("textures") && root.getAsJsonObject("textures").has("SKIN");
        } catch (JsonSyntaxException ex) {
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
