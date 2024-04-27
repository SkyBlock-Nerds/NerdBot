package net.hypixel.skyblocknerds.api.http.hypixel;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import net.hypixel.skyblocknerds.api.http.IRequest;

public interface IHypixelRequest extends IRequest {

    @RequestLine("GET /v2/player?uuid={uuid}")
    @Headers("Content-Type: application/json")
    HypixelPlayerDataResponse getPlayerData(@Param("uuid") String uuid);
}
