package net.hypixel.skyblocknerds.api.http.mojang;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.UUID;

@Getter
public class MojangUsernameResponse {

    @SerializedName("name")
    private String username;
    @SerializedName("id")
    private UUID uniqueId;
    private boolean legacy;
    private boolean demo;

}
