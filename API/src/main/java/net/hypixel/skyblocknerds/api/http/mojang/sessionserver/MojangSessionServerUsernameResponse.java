package net.hypixel.skyblocknerds.api.http.mojang.sessionserver;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;
import net.hypixel.skyblocknerds.api.gson.SerializedPath;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Getter
@ToString
public class MojangSessionServerUsernameResponse {

    @SerializedName("id")
    private UUID uniqueId;
    @SerializedName("name")
    private String username;
    private final List<Property> properties = new ArrayList<>();
    private final List<String> profileActions = new ArrayList<>();

    public @NonNull Property getProperty() {
        return this.getProperties().get(0);
    }

    @Getter
    @ToString
    public static class Property {

        private String name;
        @SerializedName("value")
        private String raw;
        private String signature;

        public String getRawJson() {
            return new String(Base64.getDecoder().decode(this.getRaw()), StandardCharsets.UTF_8);
        }

        public Value getValue() {
            return SkyBlockNerdsAPI.GSON.fromJson(this.getRawJson(), Value.class);
        }

        @Getter
        @ToString
        public static class Value {

            private Instant timestamp;
            @SerializedName("profileId")
            private UUID uniqueId;
            @SerializedName("profileName")
            private String username;
            private boolean signatureRequired;
            @SerializedPath("textures.SKIN.url")
            private String skinUrl;
            @SerializedPath("textures.CAPE.url")
            private String capeUrl;
            @SerializedPath("textures.SKIN.metadata.model")
            private String capeModel;

            public boolean isSlim() {
                if (this.getCapeModel() == null) {
                    return this.isDefaultSlim();
                }

                return this.getCapeModel().equals("slim");
            }

            public boolean isDefaultSlim() {
                return this.getUniqueId().hashCode() % 2 == 1;
            }
        }
    }
}
