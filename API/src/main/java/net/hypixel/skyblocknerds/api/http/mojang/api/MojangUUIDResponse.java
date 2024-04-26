package net.hypixel.skyblocknerds.api.http.mojang.api;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@ToString
public class MojangUUIDResponse {

    @SerializedName("name")
    private String username;
    @SerializedName("id")
    private UUID uniqueId;
    private boolean legacy;
    private boolean demo;

}
