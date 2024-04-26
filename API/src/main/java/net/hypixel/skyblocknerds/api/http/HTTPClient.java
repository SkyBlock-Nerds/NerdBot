package net.hypixel.skyblocknerds.api.http;

import feign.Feign;
import feign.codec.ErrorDecoder;
import feign.gson.GsonDecoder;
import feign.gson.GsonEncoder;
import feign.httpclient.ApacheHttpClient;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.hypixel.skyblocknerds.api.SkyBlockNerdsAPI;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class HTTPClient<R extends IRequest> {

    private final @NonNull String url;

    public final <T extends R> @NonNull T build(@NonNull Class<T> tClass) {
        return Feign.builder()
                .client(new ApacheHttpClient())
                .encoder(new GsonEncoder(SkyBlockNerdsAPI.GSON))
                .decoder(new GsonDecoder(SkyBlockNerdsAPI.GSON))
                .errorDecoder(this.getErrorDecoder())
                .requestInterceptor(context -> this.getRequestHeaders().forEach(context::header))
                .options(new feign.Request.Options(
                        5,
                        TimeUnit.SECONDS,
                        10,
                        TimeUnit.SECONDS,
                        true
                ))
                .target(tClass, this.getUrl());
    }

    protected ErrorDecoder getErrorDecoder() {
        return new ErrorDecoder.Default();
    }

    public Map<String, String> getRequestHeaders() {
        return new HashMap<>();
    }

    public final @NonNull String getUrl() {
        return String.format("https://%s", this.url.replaceFirst("^https?://", ""));
    }

}
