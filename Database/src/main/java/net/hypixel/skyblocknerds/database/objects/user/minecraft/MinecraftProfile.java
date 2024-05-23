package net.hypixel.skyblocknerds.database.objects.user.minecraft;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.hypixel.skyblocknerds.api.http.mojang.api.MojangUUIDResponse;

import java.util.UUID;

@Getter
@Setter
@ToString
public class MinecraftProfile {

    @SerializedName(value = "uuid", alternate = {"id"})
    private UUID uniqueId;
    @SerializedName(value = "name", alternate = {"username"})
    private String username;

    public MinecraftProfile() {
    }

    public MinecraftProfile(UUID uniqueId, String username) {
        this.uniqueId = uniqueId;
        this.username = username;
    }

    public MinecraftProfile(MojangUUIDResponse response) {
        this.uniqueId = response.getUniqueId();
        this.username = response.getUsername();
    }
}
