package net.hypixel.skyblocknerds.api.http.mojang.sessionserver;

import feign.FeignException;
import feign.codec.ErrorDecoder;
import net.hypixel.skyblocknerds.api.http.HTTPClient;
import net.hypixel.skyblocknerds.api.http.mojang.exception.MojangAPIException;

public class MojangSessionServerClient extends HTTPClient<IMojangSessionServerRequest> {

    public MojangSessionServerClient() {
        super("sessionserver.mojang.com");
    }

    @Override
    protected ErrorDecoder getErrorDecoder() {
        return (methodKey, response) -> {
            throw new MojangAPIException(FeignException.errorStatus(methodKey, response));
        };
    }
}