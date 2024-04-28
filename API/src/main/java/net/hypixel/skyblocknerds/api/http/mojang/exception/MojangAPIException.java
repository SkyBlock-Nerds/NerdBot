package net.hypixel.skyblocknerds.api.http.mojang.exception;

import feign.FeignException;
import lombok.Getter;
import lombok.NonNull;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;

import java.nio.charset.StandardCharsets;

@Getter
public final class MojangAPIException extends RuntimeException {

    private final @NonNull MojangErrorResponse response;

    public MojangAPIException(@NonNull FeignException exception) {
        super(exception);

        this.response = exception.responseBody()
            .map(byteBuffer -> new String(byteBuffer.array(), StandardCharsets.UTF_8))
            .map(json -> SkyBlockNerdsAPI.GSON.fromJson(json, MojangErrorResponse.class))
            .orElse(new MojangErrorResponse.Unknown());
    }
}