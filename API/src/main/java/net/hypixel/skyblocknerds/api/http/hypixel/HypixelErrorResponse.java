package net.hypixel.skyblocknerds.api.http.hypixel;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HypixelErrorResponse {

    protected boolean success;
    protected String cause;
    protected boolean throttle;
    protected boolean global;

    public static class Unknown extends HypixelErrorResponse {

        public Unknown() {
            super.success = false;
            super.cause = "Unknown Reason";
            super.throttle = false;
            super.global = false;
        }
    }
}
