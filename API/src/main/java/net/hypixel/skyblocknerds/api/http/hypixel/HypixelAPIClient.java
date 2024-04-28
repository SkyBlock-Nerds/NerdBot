package net.hypixel.skyblocknerds.api.http.hypixel;

import feign.FeignException;
import feign.codec.ErrorDecoder;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;
import net.hypixel.skyblocknerds.api.http.HTTPClient;
import net.hypixel.skyblocknerds.api.http.hypixel.exception.HypixelAPIException;

import java.util.Map;

public class HypixelAPIClient extends HTTPClient<IHypixelRequest> {

    public HypixelAPIClient() {
        super("api.hypixel.net");
    }

    @Override
    public Map<String, String> getRequestHeaders() {
        return Map.of("API-Key", SkyBlockNerdsAPI.getCommandLine().getOptionValue("hypixelApiKey"));
    }

    @Override
    protected ErrorDecoder getErrorDecoder() {
        return (methodKey, response) -> {
            throw new HypixelAPIException(FeignException.errorStatus(methodKey, response));
        };
    }
}