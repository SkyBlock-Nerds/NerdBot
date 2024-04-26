package net.hypixel.skyblocknerds.api.http.mojang.api;

import feign.FeignException;
import feign.codec.ErrorDecoder;
import net.hypixel.skyblocknerds.api.http.HTTPClient;
import net.hypixel.skyblocknerds.api.http.mojang.exception.MojangAPIException;

public class MojangAPIClient extends HTTPClient<IMojangAPIRequest> {

    public MojangAPIClient() {
        super("api.mojang.com");
    }

    @Override
    protected ErrorDecoder getErrorDecoder() {
        return (methodKey, response) -> {
            throw new MojangAPIException(FeignException.errorStatus(methodKey, response));
        };
    }
}
