package net.hypixel.skyblocknerds.api.http.hypixel;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import net.hypixel.skyblocknerds.api.http.IRequest;
import net.hypixel.skyblocknerds.api.http.hypixel.response.HypixelPlayerDataResponse;

public interface IHypixelRequest extends IRequest {

    @RequestLine("GET /v2/player?uuid={uuid}")
    @Headers("Content-Type: application/json")
    HypixelPlayerDataResponse getPlayerData(@Param("uuid") String uuid);
}
