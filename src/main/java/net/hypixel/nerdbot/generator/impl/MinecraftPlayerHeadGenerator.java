package net.hypixel.nerdbot.generator.impl;

import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.generator.ClassBuilder;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.util.Item;
import net.hypixel.nerdbot.generator.util.MinecraftHead;
import net.hypixel.nerdbot.util.Util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MinecraftPlayerHeadGenerator implements Generator {

    private static final Pattern TEXTURE_URL = Pattern.compile("(?:https?://textures.minecraft.net/texture/)?([a-zA-Z0-9]+)");

    private final String textureId;

    @Override
    public Item generate() {
        return new Item(createHead(textureId));
    }

    private BufferedImage createHead(String textureId) {
        // Checking if the texture ID is a player name
        if (textureId.length() <= 16) {
            textureId = getPlayerHeadURL(textureId);
        }

        // Checking if the texture ID is a texture URL to a skin
        Matcher textureMatcher = TEXTURE_URL.matcher(textureId);
        if (textureMatcher.matches()) {
            textureId = textureMatcher.group(1);
        }

        // Convert the texture ID to a skin URL
        BufferedImage skin;
        try {
            URL target = new URL("http://textures.minecraft.net/texture/" + textureId);
            skin = ImageIO.read(target);
        } catch (MalformedURLException e) {
            throw new GeneratorException("Malformed URL: " + textureId);
        } catch (IOException e) {
            throw new GeneratorException("Could not find skin with ID: " + textureId);
        }

        return new MinecraftHead(skin).drawHead().getImage();
    }

    private String getPlayerHeadURL(String playerName) {
        playerName = playerName.replaceAll("[^a-zA-Z0-9_]", "");

        JsonObject userUUID;
        try {
            userUUID = Util.makeHttpRequest(String.format("https://api.mojang.com/users/profiles/minecraft/%s", playerName));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeneratorException("Could not find player with name: " + playerName);
        }

        if (userUUID == null || userUUID.get("id") == null) {
            throw new GeneratorException("Could not find player with name: " + playerName);
        }

        JsonObject userProfile;
        try {
            userProfile = Util.makeHttpRequest(String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s", userUUID.get("id").getAsString()));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeneratorException("Could not find player with name: " + playerName);
        }

        if (userProfile == null || userProfile.get("properties") == null) {
            throw new GeneratorException("Could not find player with name: " + playerName);
        }

        String base64SkinData = userProfile.get("properties").getAsJsonArray().get(0).getAsJsonObject().get("value").getAsString();
        return base64ToSkinURL(base64SkinData);
    }

    /**
     * Gets the Skin ID from a Base64 String
     *
     * @param base64SkinData Base64 Skin Data
     *
     * @return the skin id
     */
    private String base64ToSkinURL(String base64SkinData) {
        JsonObject skinData = NerdBotApp.GSON.fromJson(new String(Base64.getDecoder().decode(base64SkinData)), JsonObject.class);
        return skinData.get("textures").getAsJsonObject().get("SKIN").getAsJsonObject().get("url").getAsString().replace("http://textures.minecraft.net/texture/", "");
    }

    public static class Builder implements ClassBuilder<MinecraftPlayerHeadGenerator> {
        private String texture;

        public Builder withSkin(String texture) {
            this.texture = texture;
            return this;
        }

        @Override
        public MinecraftPlayerHeadGenerator build() {
            return new MinecraftPlayerHeadGenerator(texture);
        }
    }
}
