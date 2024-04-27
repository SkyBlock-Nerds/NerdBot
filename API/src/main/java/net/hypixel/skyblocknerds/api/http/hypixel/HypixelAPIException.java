package net.hypixel.skyblocknerds.api.http.hypixel;

import feign.FeignException;
import lombok.Getter;
import lombok.NonNull;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;

import java.nio.charset.StandardCharsets;

@Getter
public final class HypixelAPIException extends RuntimeException {

    private final @NonNull HypixelErrorResponse response;

    public HypixelAPIException(@NonNull FeignException exception) {
        super(exception);

        this.response = exception.responseBody()
                .map(byteBuffer -> new String(byteBuffer.array(), StandardCharsets.UTF_8))
                .map(json -> SkyBlockNerdsAPI.GSON.fromJson(json, HypixelErrorResponse.class))
                .orElse(new HypixelErrorResponse.Unknown());
    }
}
