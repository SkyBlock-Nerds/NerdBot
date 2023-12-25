package net.hypixel.nerdbot.util.wiki;

import io.github.fastily.jwiki.core.NS;
import io.github.fastily.jwiki.core.Wiki;
import okhttp3.HttpUrl;

import java.util.regex.Pattern;

public class MediaWikiAPI {

    public static final String BASE_URL = "https://wiki.hypixel.net/api.php";
    public static final Pattern URL_PATTERN = Pattern.compile("https?://wiki.hypixel.net/(.*)");

    public static boolean isEditor(String username) {
        Wiki wiki = new Wiki.Builder()
            .withApiEndpoint(HttpUrl.parse(BASE_URL))
            .build();

        return wiki.getCategoryMembers("Editor", NS.USER).stream()
            .map(s -> s.replace("User:", ""))
            .map(s -> s.replace(" ", "_"))
            .anyMatch(s -> s.equalsIgnoreCase(username));
    }

    public static boolean isValidWikiUrl(String url) {
        return URL_PATTERN.matcher(url).matches();
    }
}
