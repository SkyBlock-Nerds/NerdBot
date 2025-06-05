package net.hypixel.nerdbot.service.orangejuice;

import org.json.JSONArray;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class SearchService extends OrangeJuiceApiService {

    public static List<String> getTooltipSides(String searchTerm) throws IOException, InterruptedException {
        return mapJsonToList(makeBasicSearchEndpointRequest("/tooltip-side", searchTerm));
    }

    public static List<String> getStats(String searchTerm) throws IOException, InterruptedException {
        return mapJsonToList(makeBasicSearchEndpointRequest("/stat", searchTerm));
    }

    public static List<String> getRarities(String searchTerm) throws IOException, InterruptedException {
        return mapJsonToList(makeBasicSearchEndpointRequest("/rarity", searchTerm));
    }

    public static List<String> getItemIds(String searchTerm) throws IOException, InterruptedException {
        return mapJsonToList(makeBasicSearchEndpointRequest("/item-id", searchTerm));
    }

    public static List<String> getIcons(String searchTerm) throws IOException, InterruptedException {
        return mapJsonToList(makeBasicSearchEndpointRequest("/icon", searchTerm));
    }

    public static List<String> getGemstones(String searchTerm) throws IOException, InterruptedException {
        return mapJsonToList(makeBasicSearchEndpointRequest("/gemstone", searchTerm));
    }

    private static String makeBasicSearchEndpointRequest(String endpoint, String searchTerm) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(OrangeJuiceApiService.BASE_URL + "/search" + endpoint + (searchTerm != null ? "?searchTerm=" + searchTerm : "")))
            .GET()
            .build();

        return OrangeJuiceApiService.httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static List<String> mapJsonToList(String json) {
        JSONArray jsonArray = new JSONArray(json);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            result.add(jsonArray.getString(i));
        }
        return result;
    }
}