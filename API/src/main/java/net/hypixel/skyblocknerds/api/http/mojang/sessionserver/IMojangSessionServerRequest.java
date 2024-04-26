package net.hypixel.skyblocknerds.api.http.mojang.sessionserver;

import feign.Param;
import feign.RequestLine;
import lombok.NonNull;
import net.hypixel.skyblocknerds.api.http.IRequest;

import javax.annotation.Nonnull;
import java.util.UUID;

public interface IMojangSessionServerRequest extends IRequest {

    @RequestLine("GET /session/minecraft/profile/{uuid}")
    @Nonnull
    MojangSessionServerUsernameResponse getUsername(@NonNull @Param("uuid") UUID uuid);
}
