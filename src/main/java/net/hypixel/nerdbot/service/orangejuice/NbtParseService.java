package net.hypixel.nerdbot.service.orangejuice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import net.hypixel.nerdbot.service.orangejuice.requestmodels.generator.TooltipGeneratorRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;


public class NbtParseService extends OrangeJuiceApiService {

    public ParsedNbt parseNbt(String data, Integer alpha, Integer padding) throws InterruptedException, IOException {
        String query = "";
        if (alpha != null && padding != null) {
            query = "?alpha=" + alpha + "&padding=" + padding;
        } else if (alpha != null) {
            query = "?alpha=" + alpha;
        } else if (padding != null) {
            query = "?padding=" + padding;
        }

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(OrangeJuiceApiService.BASE_URL + "/nbtparse" + query))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(data))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            Gson gson = new GsonBuilder()
                .registerTypeAdapter(byte[].class, new Base64ByteArrayAdapter())
                .create();
            return gson.fromJson(response.body(), ParsedNbt.class);
        } else {
            throw new RuntimeException("An API error occurred while generating image (" + response.statusCode() + "): " + response.body());
        }
    }

    @Getter
    public class ParsedNbt {
        private final TooltipGeneratorRequest tooltipGeneratorRequest;
        private final byte[] image; // Can also be a GIF. Yes an GIF is an image, I don't care about your feelings

        public ParsedNbt(TooltipGeneratorRequest tooltipGeneratorRequest, byte[] image) {
            this.tooltipGeneratorRequest = tooltipGeneratorRequest;
            this.image = image;
        }
    }

    private class Base64ByteArrayAdapter extends TypeAdapter<byte[]> {
        @Override
        public void write(JsonWriter out, byte[] value) throws IOException {
            out.value(Base64.getEncoder().encodeToString(value));
        }

        @Override
        public byte[] read(JsonReader in) throws IOException {
            String base64 = in.nextString();
            return Base64.getDecoder().decode(base64);
        }
    }
}