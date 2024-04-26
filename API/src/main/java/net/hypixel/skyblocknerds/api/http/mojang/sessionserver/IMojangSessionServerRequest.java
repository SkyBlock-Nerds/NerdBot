package net.hypixel.skyblocknerds.api.http.mojang;

import feign.Param;
import feign.RequestLine;
import lombok.NonNull;
import net.hypixel.skyblocknerds.api.http.IRequest;

import javax.annotation.Nonnull;

public interface IMojangSessionServerRequest extends IRequest {

    @RequestLine("GET /session/minecraft/profile/{uuid}")
    @Nonnull
    MojangUsernameResponse getUniqueId(@NonNull @Param("uuid") String username);
}
