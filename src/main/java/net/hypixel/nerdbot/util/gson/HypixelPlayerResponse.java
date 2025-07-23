package net.hypixel.nerdbot.util.gson;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import net.hypixel.nerdbot.util.UUIDUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class HypixelPlayerResponse {

    private boolean success;
    private String cause;
    private Player player;

    public boolean isSuccess() {
        return success;
    }

    public Player getPlayer() {
        return player;
    }

    public String getCause() {
        return Optional.ofNullable(this.cause).orElse("Unknown cause.");
    }

    public static class Player {

        @SerializedName("_id")
        @Getter
        private String hypixelId;
        private String uuid;
        @SerializedName("displayname")
        @Getter
        private String displayName;
        @SerializedName("channel")
        @Getter
        private String chatChannel;
        @Getter
        private Instant firstLogin;
        @Getter
        private Instant lastLogin;
        @Getter
        private Instant lastLogout;
        @Getter
        private long networkExp;
        @Getter
        private long karma;
        @Getter
        private int achievementPoints;
        @Getter
        private long totalDailyRewards;
        @Getter
        private long totalRewards;
        @Getter
        private String mcVersionRp;
        @Getter
        private String mostRecentGameType;
        @SerializedName("playername")
        private String playerName;
        @Getter
        private List<String> knownAliases;
        @Getter
        private SocialMedia socialMedia;
        private List<Object> achievementsOneTime;
        private transient List<String> achievementsOneTimeFixed;
        @Getter
        private String currentClickEffect;
        @Getter
        private String currentGadget;
        @SerializedName("claimed_potato_talisman")
        @Getter
        private Instant claimedPotatoTalisman;
        @SerializedName("skyblock_free_cookie")
        @Getter
        private Instant skyblockFreeCookie;
        @SerializedName("claimed_century_cake")
        @Getter
        private Instant claimedCenturyCake;
        @SerializedName("scorpius_bribe_120")
        @Getter
        private Instant scorpiusBribe120;
        @Getter
        private Map<String, Long> voting;
        @Getter
        private Map<String, Integer> petConsumables;
        @Getter
        private Map<String, Integer> achievements;
        @Getter
        private Map<String, Instant> achievementRewardsNew;

        // Stats (Only SkyBlock Currently)
        @Getter
        private Stats stats;

        public List<String> getAchievementsOneTime() {
            if (this.achievementsOneTimeFixed == null) {
                this.achievementsOneTimeFixed = this.achievementsOneTime.stream()
                    .filter(obj -> (obj instanceof String))
                    .map(String::valueOf)
                    .toList();
            }

            return achievementsOneTimeFixed;
        }

        public UUID getUniqueId() {
            return UUIDUtils.toUUID(this.uuid);
        }

    }

    public static class SocialMedia {

        @Getter
        private final Map<Service, String> links = new HashMap<>();
        private boolean prompt;

        public enum Service {

            TWITTER,
            YOUTUBE,
            INSTAGRAM,
            TWITCH,
            DISCORD,
            HYPIXEL

        }

    }

    public static class Stats {

        @SerializedName("SkyBlock")
        @Getter
        private SkyBlock skyBlock;

        public static class SkyBlock {

            private final Map<String, Profile> profiles = new HashMap<>();

            public Optional<Set<Profile>> getProfiles() {
                return this.profiles.isEmpty() ? Optional.empty() : Optional.of(new HashSet<>(this.profiles.values()));
            }

            public static class Profile {

                @SerializedName("profile_id")
                private String profileId;
                @SerializedName("cute_name")
                private String cuteName;

                public String getCuteName() {
                    return this.cuteName;
                }

                public UUID getUniqueId() {
                    return UUIDUtils.toUUID(this.profileId);
                }

            }
        }
    }
}
