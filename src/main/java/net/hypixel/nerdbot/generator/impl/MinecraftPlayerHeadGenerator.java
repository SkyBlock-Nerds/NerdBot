package net.hypixel.nerdbot.generator.impl;

import com.google.gson.JsonObject;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.item.GeneratedObject;
import net.hypixel.nerdbot.generator.skull.RenderedPlayerSkull;
import net.hypixel.nerdbot.util.ImageUtil;
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
        return skinData.get("textures").getAsJsonObject().get("SKIN").getAsJsonObject().get("url").getAsString().replace("http://textures.minecraft.net/texture/", "");
    }

    @Override
    public GeneratedObject generate() {
        return new GeneratedObject(createHead(textureId));
    }

    private BufferedImage createHead(String textureId) {
        if (textureId == null) {
            textureId = DEFAULT_SKIN_VALUE;
        }

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
            userUUID = Util.makeHttpRequest(String.format("https://api.mojang.com/users/profiles/minecraft/%s", playerName));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GeneratorException("Could not find player with name: `%s`", playerName);
        }

        if (userUUID == null || userUUID.get("id") == null) {
            throw new GeneratorException("Could not find player with name: `%s`", playerName);
        }

        JsonObject userProfile;
        try {
            userProfile = Util.makeHttpRequest(String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s", userUUID.get("id").getAsString()));
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

    public static class Builder implements ClassBuilder<MinecraftPlayerHeadGenerator> {
        private String texture;
        private int scale;

        public Builder withSkin(String texture) {
            if (Util.isValidBase64(texture)) {
                this.texture = base64ToSkinURL(texture);
            } else {
                this.texture = texture;
            }
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
