package net.hypixel.nerdbot.core.wiki;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.hypixel.nerdbot.core.HttpClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class WikiImageDownloader {

    private static final String URL_REGEX = ".*_?JE[1-9]_?.*";
    private static final int BATCH_SIZE = 500;

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
        {800, 800}
    };

    private static boolean debug = false;
    private static String baseUrl;
    private static String primaryFolderName;

    public static void main(String[] args) {
        debug = Arrays.asList(args).contains("--debug");
        baseUrl = Arrays.stream(args).filter(arg -> arg.startsWith("--url=")).findFirst().map(arg -> arg.substring(6)).orElse(baseUrl);

        if (baseUrl == null) {
            System.err.println("No URL provided! Please provide a valid MediaWiki URL using the --url argument");
            System.exit(-1);
        }

        String folderName = baseUrl.substring(baseUrl.indexOf("://") + 3, baseUrl.indexOf("/", baseUrl.indexOf("://") + 3)).replace(".", "_");
        primaryFolderName = "./src/main/resources/wiki-image-export/" + folderName + "/";

        if (debug) {
            System.out.println("Debug mode enabled");
        }

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

        System.out.println("Starting image scraping process for URL: " + baseUrl);

        do {
            String apiUrl = buildApiUrl(continueParam);
            JsonObject responseJson = makeApiRequest(apiUrl);

            if (responseJson.has("continue")) {
                continueParam = responseJson.getAsJsonObject("continue").get("aicontinue").getAsString();
                printDebug("Continue param: " + continueParam);
            } else {
                continueParam = null;
                printDebug("Fetched all pages for URL: " + baseUrl);
            }

            JsonArray imagesArray = responseJson.getAsJsonObject("query").getAsJsonArray("allimages");

            for (int i = 0; i < imagesArray.size(); i++) {
                String imageUrl = imagesArray.get(i).getAsJsonObject().get("url").getAsString();

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

        System.out.println("\rFinished image scraping process for URL: " + baseUrl);
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
        StringBuilder apiUrl = new StringBuilder(baseUrl);
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

    private static JsonObject makeApiRequest(String apiUrl) throws IOException {
        printDebug("Making request to URL: " + apiUrl);
        try {
            return HttpClient.getJson(apiUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted for URL: " + apiUrl, e);
        }
    }

    public static BufferedImage downloadImage(String imageUrl) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(imageUrl))
            .GET()
            .build();

        try {
            HttpResponse<InputStream> response = HttpClient.getClient().send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Failed to download image. Status: " + response.statusCode() + " URL: " + imageUrl);
            }

            try (InputStream inputStream = response.body()) {
                printDebug("Downloaded image: " + imageUrl);
                return ImageIO.read(inputStream);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Image download interrupted for URL: " + imageUrl, e);
        }
    }

    private static void saveImageToFolder(BufferedImage image, String imageName) throws IOException {
        File outputfile = new File(primaryFolderName + imageName + ".png");

        if (outputfile.getParentFile().mkdirs()) {
            printDebug("Created directory: " + outputfile.getParentFile().getAbsolutePath());
        }

        ImageIO.write(image, "png", outputfile);
        printDebug("Saved image: " + imageName + " in folder: " + outputfile.getParentFile().getAbsolutePath());
    }

    private static void printDebug(String message) {
        if (debug) {
            System.out.println(message);
        }
    }
}
