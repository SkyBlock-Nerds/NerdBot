package net.hypixel.skyblocknerds.api.http.hypixel;

import feign.FeignException;
import feign.codec.ErrorDecoder;
import net.hypixel.skyblocknerds.api.http.HTTPClient;
import net.hypixel.skyblocknerds.api.http.mojang.api.IMojangAPIRequest;
import net.hypixel.skyblocknerds.api.http.mojang.exception.MojangAPIException;

import java.util.Map;

public class HypixelClient extends HTTPClient<IMojangAPIRequest> {

    public HypixelClient() {
        super("api.hypixel.net");
    }

    @Override
    public Map<String, String> getRequestHeaders() {
        return Map.of("API-Key", "API_KEY_HERE");
    }

    @Override
    protected ErrorDecoder getErrorDecoder() {
        return (methodKey, response) -> {
            throw new MojangAPIException(FeignException.errorStatus(methodKey, response));
        };
    }
}