package net.hypixel.skyblocknerds.api.http.mojang.api;

import feign.Param;
import feign.RequestLine;
import lombok.NonNull;
import net.hypixel.skyblocknerds.api.http.IRequest;

import javax.annotation.Nonnull;

public interface IMojangAPIRequest extends IRequest {

    @RequestLine("GET /users/profiles/minecraft/{username}")
    @Nonnull
    MojangUUIDResponse getUniqueId(@NonNull @Param("username") String username);

}