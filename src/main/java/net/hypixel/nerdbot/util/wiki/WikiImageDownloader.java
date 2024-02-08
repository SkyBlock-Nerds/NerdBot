package net.hypixel.nerdbot.util.wiki;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class WikiImageDownloader {

    private static final String BASE_URL = "https://minecraft.wiki/api.php";
    private static final String FOLDER_NAME = BASE_URL.substring(BASE_URL.indexOf("://") + 3, BASE_URL.indexOf("/", BASE_URL.indexOf("://") + 3)).replace(".", "_");
    private static final String PRIMARY_FOLDER = "./src/main/resources/wiki_image_export/" + FOLDER_NAME + "/";
    private static final String URL_REGEX = ".*_?JE[1-9]_?.*";
    private static final int BATCH_SIZE = 500;
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    private static final Request.Builder REQUEST_BUILDER = new Request.Builder();
    private static final boolean DEBUG = false;

    private static final String[] BLACKLISTED_WORDS = {
        "inventory",
        "texture",
        "item",
        "fast",
        "no_tint",
        "classic",
        "slim",
        "sample",
        "model",
        "bottom",
        "facing",
        "shown",
        "away",
        "connect",
        "hitbox",
        "font",
        "example",
        "diagram"
    };
    private static final int[][] WANTED_IMAGE_DIMENSIONS = {
        {150, 300},
        {300, 300},
        {300, 150},
        {300, 464},
        {300, 468},
        {300, 505},
        {400, 680},
        {600, 900},
        {600, 900},
        {800, 800},
        {854, 480}
    };

    public static void main(String[] args) {
        try {
            scrapeImages();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void scrapeImages() throws IOException {
        String continueParam = null;
        Map<String, Integer> imageDimensions = new HashMap<>();
        int total = 0;

        System.out.println("Starting image scraping process for URL: " + BASE_URL);

        do {
            String apiUrl = buildApiUrl(continueParam);
            String jsonResponse = makeApiRequest(apiUrl);
            JSONObject responseJson = new JSONObject(jsonResponse);

            if (responseJson.has("continue")) {
                continueParam = responseJson.getJSONObject("continue").getString("aicontinue");
                printDebug("Continue param: " + continueParam);
            } else {
                continueParam = null;
                printDebug("Fetched all pages for URL: " + BASE_URL);
            }

            JSONArray imagesArray = responseJson.getJSONObject("query").getJSONArray("allimages");

            for (int i = 0; i < imagesArray.length(); i++) {
                String imageUrl = imagesArray.getJSONObject(i).getString("url");

                if (!imageUrl.contains(".png")
                    || imageUrl.matches(".*BE[1-9]?_?.*")
                    || !imageUrl.matches(URL_REGEX)
                    || Arrays.stream(BLACKLISTED_WORDS).anyMatch(s -> imageUrl.toLowerCase().contains(s))
                ) {
                    continue;
                }

                String imageName = imageUrl.substring(imageUrl.lastIndexOf("/") + 1, imageUrl.lastIndexOf("."))
                    .replaceAll("%[1-9]{2}", "")
                    .replaceAll("_?JE[1-9]_?", "")
                    .toLowerCase();

                BufferedImage image = downloadImage(imageUrl);
                int imageWidth = image.getWidth();
                int imageHeight = image.getHeight();

                imageDimensions.put(imageWidth + "x" + imageHeight, imageDimensions.getOrDefault(imageWidth + "x" + imageHeight, 0) + 1);

                if (Arrays.stream(WANTED_IMAGE_DIMENSIONS).noneMatch(dimensions -> dimensions[0] == imageWidth && dimensions[1] == imageHeight)) {
                    printDebug("\rSkipping image: " + imageName + " with dimensions: " + imageWidth + "x" + imageHeight + " since they are not wanted image dimensions");
                    continue;
                }

                saveImageToFolder(downloadImage(imageUrl), imageName);
                System.out.print("\rDownloaded image: " + imageName + " with dimensions: " + image.getWidth() + "x" + image.getHeight());
                total++;
            }
        } while (continueParam != null);

        System.out.println("\rFinished image scraping process for URL: " + BASE_URL);
        System.out.println("Total images found: " + imageDimensions.values().stream().mapToInt(Integer::intValue).sum());
        System.out.println("Total images downloaded: " + total);

        printDebug("-------------------------------------");
        printDebug("All Image Dimension Appearances:");
        imageDimensions.entrySet().stream()
            .sorted((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()))
            .forEach(stringIntegerEntry -> {
                printDebug(stringIntegerEntry.getKey() + " - " + stringIntegerEntry.getValue());
            });
        printDebug("-------------------------------------");
    }

    private static String buildApiUrl(String continueParam) {
        StringBuilder apiUrl = new StringBuilder(BASE_URL);
        apiUrl.append("?action=query");
        apiUrl.append("&ailimit=").append(BATCH_SIZE);
        apiUrl.append("&aiprop=url");
        apiUrl.append("&format=json");
        apiUrl.append("&list=allimages");
        apiUrl.append("&aimime=image/png");

        if (continueParam != null) {
            apiUrl.append("&aicontinue=").append(continueParam);
        }

        return apiUrl.toString();
    }

    private static String makeApiRequest(String apiUrl) throws IOException {
        Request request = new Request.Builder()
            .url(apiUrl)
            .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            printDebug("Making request to URL: " + apiUrl);
            return response.body().string();
        }
    }

    public static BufferedImage downloadImage(String imageUrl) throws IOException {
        Request request = REQUEST_BUILDER.url(imageUrl).build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download image: " + response);
            }

            try (InputStream inputStream = response.body().byteStream()) {
                printDebug("Downloaded image: " + imageUrl);
                return ImageIO.read(inputStream);
            }
        }
    }

    private static void saveImageToFolder(BufferedImage image, String imageName) throws IOException {
        File outputfile = new File(PRIMARY_FOLDER + imageName + ".png");

        if (outputfile.getParentFile().mkdirs()) {
            printDebug("Created directory: " + outputfile.getParentFile().getAbsolutePath());
        }

        ImageIO.write(image, "png", outputfile);
        printDebug("Saved image: " + imageName + " in folder: " + outputfile.getParentFile().getAbsolutePath());
    }

    private static void printDebug(String message) {
        if (DEBUG) {
            System.out.println(message);
        }
    }
}
