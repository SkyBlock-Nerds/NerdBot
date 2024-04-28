package net.hypixel.skyblocknerds.api.http.hypixel.response;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@ToString
@Getter
public class HypixelPlayerDataResponse {

    private boolean success;
    private Player player;

    @ToString
    @Getter
    public static class Player {

        private UUID uuid;
        @SerializedName("displayname")
        private String displayName;
        private String rank;
        private String packageRank;
        private String newPackageRank;
        private String monthlyPackageRank;
        private SocialMedia socialMedia;

        @Getter
        @ToString
        public static class SocialMedia {

            private Links links;

            @Getter
            @ToString
            public static class Links {
                @SerializedName("DISCORD")
                private String discordUsername;
            }
        }
    }
}
