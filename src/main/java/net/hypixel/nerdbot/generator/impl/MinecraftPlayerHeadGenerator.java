package net.hypixel.nerdbot.generator.impl;

import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.GeneratedItem;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
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

@Log4j2
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MinecraftPlayerHeadGenerator implements Generator {

    private static final String DEFAULT_SKIN_VALUE = "31f477eb1a7beee631c2ca64d06f8f68fa93a3386d04452ab27f43acdf1b60cb"; // TODO find better value
    private static final Pattern TEXTURE_URL = Pattern.compile("(?:https?://textures.minecraft.net/texture/)?([a-zA-Z0-9]+)");

    private final String textureId;

    @Override
    public GeneratedItem generate() {
        return new GeneratedItem(createHead(textureId));
    }

    private BufferedImage createHead(String textureId) {
        if (textureId == null) {
            System.out.println("Texture ID is null, using default");
            textureId = DEFAULT_SKIN_VALUE;
        }

        // Checking if the texture ID is a player name
        if (textureId.length() <= 16) {
            System.out.println("Texture ID is a player name, getting player head URL");
            textureId = getPlayerHeadURL(textureId);
        }

        // Checking if the texture ID is a texture URL to a skin
        Matcher textureMatcher = TEXTURE_URL.matcher(textureId);
        if (textureMatcher.matches()) {
            System.out.println("Texture ID is a texture URL to a skin");
            textureId = textureMatcher.group(1);
            System.out.println("Texture ID: " + textureId);
        }

        // Convert the texture ID to a skin URL
        BufferedImage skin;
        try {
            URL target = new URL("http://textures.minecraft.net/texture/" + textureId);
            skin = ImageIO.read(target);
            System.out.println("Skin: " + target);
        } catch (MalformedURLException exception) {
            throw new GeneratorException("Malformed URL: " + textureId);
        } catch (IOException exception) {
            log.error("Could not find skin with ID: " + textureId, exception);
            throw new GeneratorException("Could not find skin with ID: " + textureId);
        }

        return new MinecraftHead(skin).drawHead().getImage();
    }

    private String getPlayerHeadURL(String playerName) {
        playerName = playerName.replaceAll("[^a-zA-Z0-9_]", "");

        System.out.println("Getting player head URL for player: " + playerName);

        JsonObject userUUID;
        try {
            userUUID = Util.makeHttpRequest(String.format("https://api.mojang.com/users/profiles/minecraft/%s", playerName));
            System.out.println("User UUID: " + userUUID);
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
        System.out.println("Base64 Skin Data: " + base64SkinData);
        return base64ToSkinURL(base64SkinData);
    }

    /**
     * Gets the Skin ID from a Base64 String
     *
     * @param base64SkinData Base64 Skin Data
     *
     * @return the skin id
     */
    private static String base64ToSkinURL(String base64SkinData) {
        JsonObject skinData = NerdBotApp.GSON.fromJson(new String(Base64.getDecoder().decode(base64SkinData)), JsonObject.class);
        System.out.println("Skin Data: " + skinData);
        return skinData.get("textures").getAsJsonObject().get("SKIN").getAsJsonObject().get("url").getAsString().replace("http://textures.minecraft.net/texture/", "");
    }

    public static class Builder implements ClassBuilder<MinecraftPlayerHeadGenerator> {
        private String texture;

        public Builder withSkin(String texture) {
            this.texture = texture;
            return this;
        }

        public Builder parseBase64String(String base64) {
            this.texture = base64ToSkinURL(base64);
            return this;
        }

        @Override
        public MinecraftPlayerHeadGenerator build() {
            return new MinecraftPlayerHeadGenerator(texture);
        }
    }
}
