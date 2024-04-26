package net.hypixel.skyblocknerds.api.http.mojang.exception;

import com.google.gson.annotations.SerializedName;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MojangErrorResponse {

    @SerializedName("error")
    protected String type;
    @SerializedName("errorMessage")
    protected String reason;
    protected String path;

    public static class Unknown extends MojangErrorResponse {

        public Unknown() {
            super.type = "UNKNOWN";
            super.reason = "Unknown Reason";
            super.path = "";
        }
    }
}
