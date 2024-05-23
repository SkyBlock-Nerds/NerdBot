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

    private DiscordUser() {
    }

    public DiscordUser(String discordId) {
        this.discordId = discordId;
        this.lastKnownUsername = null;
        this.language = UserLanguage.ENGLISH;
        this.badges = new ArrayList<>();
        this.minecraftProfile = null;
    }

    public boolean hasMinecraftProfile() {
        return this.minecraftProfile != null && this.minecraftProfile.getUniqueId() != null;
    }

    public void linkMinecraftProfile(String username) {
        this.minecraftProfile = new MinecraftProfile(SkyBlockNerdsAPI.MOJANG_REQUEST.getUniqueId(username));
    }

    public void linkMinecraftProfile(UUID uuid, String username) {
        this.minecraftProfile = new MinecraftProfile(uuid, username);
    }
}
