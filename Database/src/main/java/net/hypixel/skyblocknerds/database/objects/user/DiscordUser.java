package net.hypixel.skyblocknerds.database.objects.user;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;
import net.hypixel.skyblocknerds.api.translation.UserLanguage;
import net.hypixel.skyblocknerds.database.objects.user.badge.BadgeEntry;
import net.hypixel.skyblocknerds.database.objects.user.minecraft.MinecraftProfile;
import net.hypixel.skyblocknerds.database.objects.user.warning.WarningEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
@ToString
public class DiscordUser {

    private String discordId;
    private String lastKnownUsername;
    private UserLanguage language;
    private List<BadgeEntry> badges;
    private List<WarningEntry> warnings;
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

    /**
     * Checks if this {@link DiscordUser} has any {@link WarningEntry warnings}
     *
     * @return {@code true} if the {@link DiscordUser} has any {@link WarningEntry warnings}, otherwise {@code false}
     */
    public boolean hasWarnings() {
        return this.warnings != null && !this.warnings.isEmpty();
    }

    /**
     * Checks if this {@link DiscordUser} has any {@link WarningEntry warnings} from the last given amount of days
     *
     * @param days The amount of days to check for recent warnings
     *
     * @return {@code true} if the {@link DiscordUser} has any {@link WarningEntry warnings} from the last given amount of days, otherwise {@code false}
     */
    public boolean hasRecentWarnings(int days) {
        return !getRecentWarnings(days).isEmpty();
    }

    /**
     * Return all applicable {@link WarningEntry warnings} for this {@link DiscordUser} from the last given amount of days
     *
     * @return a {@link List} of {@link WarningEntry warnings} from the last given amount of days
     */
    public List<WarningEntry> getRecentWarnings(int days) {
        return warnings.stream().filter(warning -> System.currentTimeMillis() - warning.getTimestamp() <= TimeUnit.DAYS.toMillis(days)).toList();
    }
}
