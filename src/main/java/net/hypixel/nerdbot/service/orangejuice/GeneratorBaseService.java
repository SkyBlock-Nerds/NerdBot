package net.hypixel.nerdbot.service.orangejuice;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class GeneratorBaseService extends OrangeJuiceApiService {

    protected byte[] generateImage(String endpoint, Object data) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(OrangeJuiceApiService.BASE_URL + "/generator" + endpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(data)))
            .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new RuntimeException("An API error occurred while generating image (" + response.statusCode() + "): " + response.body());
        }
    }
}