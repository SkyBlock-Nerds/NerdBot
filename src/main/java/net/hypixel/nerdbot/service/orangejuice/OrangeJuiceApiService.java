package net.hypixel.nerdbot.service.orangejuice;

import net.hypixel.nerdbot.NerdBotApp;

import java.net.http.HttpClient;

public class OrangeJuiceApiService {
    protected static final String BASE_URL;
    protected static final HttpClient httpClient = HttpClient.newHttpClient();

    static {
        BASE_URL = NerdBotApp.getBot().getConfig().getImageGeneratorProvider();
    }
}