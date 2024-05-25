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

    /**
     * Creates a new {@link MinecraftProfile} with the given {@link UUID} and {@link String username}
     *
     * @param uniqueId The {@link UUID} of the Minecraft profile
     * @param username The username of the Minecraft profile
     */
    public MinecraftProfile(UUID uniqueId, String username) {
        this.uniqueId = uniqueId;
        this.username = username;
    }

    /**
     * Creates a new {@link MinecraftProfile} from a {@link MojangUUIDResponse} object
     *
     * @param response The {@link MojangUUIDResponse} object
     */
    public MinecraftProfile(MojangUUIDResponse response) {
        this.uniqueId = response.getUniqueId();
        this.username = response.getUsername();
    }
}
