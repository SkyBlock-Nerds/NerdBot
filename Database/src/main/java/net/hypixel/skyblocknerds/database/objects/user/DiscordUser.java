package net.hypixel.skyblocknerds.database.objects.user;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;
import net.hypixel.skyblocknerds.api.translation.UserLanguage;
import net.hypixel.skyblocknerds.database.objects.user.badge.BadgeEntry;
import net.hypixel.skyblocknerds.database.objects.user.minecraft.MinecraftProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@ToString
public class DiscordUser {

    private String discordId;
    private String lastKnownUsername;
    private UserLanguage language;
    private List<BadgeEntry> badges;
    private MinecraftProfile minecraftProfile;

    /**
     * Creates a new {@link DiscordUser} with the given {@link String Discord ID}
     * <p>
     * The {@link DiscordUser} will have no linked {@link MinecraftProfile}
     * and the {@link UserLanguage} will be set to {@link UserLanguage#ENGLISH}
     * by default
     * <br>
     * The {@link List} of {@link BadgeEntry badges} will be empty
     * by default
     * <br>
     * The {@link String lastKnownUsername} will be {@code null}
     * by default
     * <br>
     * The {@link UserLanguage language} will be set to {@link UserLanguage#ENGLISH}
     * by default
     * <br>
     * The {@link List} of {@link BadgeEntry badges} will be empty
     * by default
     * <br>
     * The {@link MinecraftProfile minecraftProfile} will be {@code null}
     * by default
     *
     * @param discordId The {@link String Discord ID} of the {@link DiscordUser}
     */
    public DiscordUser(String discordId) {
        this.discordId = discordId;
        this.lastKnownUsername = null;
        this.language = UserLanguage.ENGLISH;
        this.badges = new ArrayList<>();
        this.minecraftProfile = null;
    }

    /**
     * Checks if this {@link DiscordUser} has a linked {@link MinecraftProfile}
     *
     * @return {@code true} if the {@link DiscordUser} has a linked {@link MinecraftProfile}, otherwise {@code false}
     */
    public boolean hasMinecraftProfile() {
        return this.minecraftProfile != null && this.minecraftProfile.getUniqueId() != null;
    }

    /**
     * Links a Minecraft profile to this {@link DiscordUser} using the Mojang API
     *
     * @param username The username of the Minecraft profile
     */
    public void linkMinecraftProfile(String username) {
        this.minecraftProfile = new MinecraftProfile(SkyBlockNerdsAPI.MOJANG_REQUEST.getUniqueId(username));
    }

    /**
     * Links a Minecraft profile to this {@link DiscordUser}
     *
     * @param uuid     The {@link UUID} of the Minecraft profile
     * @param username The username of the Minecraft profile
     */
    public void linkMinecraftProfile(UUID uuid, String username) {
        this.minecraftProfile = new MinecraftProfile(uuid, username);
    }
}
