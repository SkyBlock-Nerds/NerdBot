package net.hypixel.nerdbot.util;

import com.google.gson.JsonObject;
import com.vdurmont.emoji.EmojiManager;
import io.prometheus.client.Summary;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.entities.channel.middleman.StandardGuildChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import net.hypixel.nerdbot.NerdBotApp;
import net.hypixel.nerdbot.api.database.model.user.DiscordUser;
import net.hypixel.nerdbot.api.database.model.user.stats.MojangProfile;
import net.hypixel.nerdbot.bot.config.channel.AlphaProjectConfig;
import net.hypixel.nerdbot.bot.config.suggestion.SuggestionConfig;
import net.hypixel.nerdbot.cache.EmojiCache;
import net.hypixel.nerdbot.cache.suggestion.Suggestion;
import net.hypixel.nerdbot.command.GeneratorCommands;
import net.hypixel.nerdbot.metrics.PrometheusMetrics;
import net.hypixel.nerdbot.repository.DiscordUserRepository;
import net.hypixel.nerdbot.util.exception.HttpException;
import net.hypixel.nerdbot.util.gson.HypixelPlayerResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Log4j2
public class Util {

    public static final Pattern SUGGESTION_TITLE_REGEX = Pattern.compile("(?i)\\[(.*?)]");
    public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");
    public static final DecimalFormat COMMA_SEPARATED_FORMAT = new DecimalFormat("#,###");
    public static final DateTimeFormatter REGULAR_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss ZZZ").withZone(ZoneId.systemDefault());
    public static final DateTimeFormatter FILE_NAME_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    // UUID Pattern Matching
    public static final Pattern UUID_REGEX = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89aAbB][a-f0-9]{3}-[a-f0-9]{12}");
    public static final Pattern TRIMMED_UUID_REGEX = Pattern.compile("[a-f0-9]{12}4[a-f0-9]{3}[89aAbB][a-f0-9]{15}");
    private static final Pattern ADD_UUID_HYPHENS_REGEX = Pattern.compile("([a-f0-9]{8})([a-f0-9]{4})(4[a-f0-9]{3})([89aAbB][a-f0-9]{3})([a-f0-9]{12})");
    @Deprecated
    private static final String MINECRAFT_USERNAME_REGEX = "^[a-zA-Z0-9_]{2,16}";
    @Deprecated
    private static final String SURROUND_REGEX = "\\|([^|]+)\\||\\[([^\\[]+)\\]|\\{([^\\{]+)\\}|\\(([^\\(]+)\\)";
    public static final String[] PROJECT_CHANNEL_NAMES = {
        "project",
        "projects",
        "projəct",
        "nerd-project",
        "nerds-project"
    };
    public static final String[] SPECIAL_ROLES = {"Apex Nerd", "Ultimate Nerd", "Ultimate Nerd But Red"};

    private Util() {
    }

    public static boolean isAprilFirst() {
        return Calendar.getInstance().get(Calendar.MONTH) == Calendar.APRIL && Calendar.getInstance().get(Calendar.DAY_OF_MONTH) == 1;
    }

    public static boolean isDayOfMonth(int dayOfMonth) {
        return Calendar.getInstance().get(Calendar.DAY_OF_MONTH) == dayOfMonth;
    }

    public static List<String> splitString(String text, int size) {
        List<String> parts = new ArrayList<>();

        for (int i = 0; i < text.length(); i += size) {
            parts.add(text.substring(i, Math.min(text.length(), i + size)));
        }

        return parts;
    }

    /**
     * Get the branch name from the BRANCH_NAME environment variable
     *
     * @return The branch name, or "unknown" if the variable is not set
     */
    public static String getBranchName() {
        String branchName = System.getenv("BRANCH_NAME");
        return isNullOrEmpty(branchName) ? "unknown" : branchName;
    }

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    public static String getDockerContainerId() {
        try {
            return Files.readString(Path.of("/etc/hostname")).trim();
        } catch (IOException e) {
            log.error("Failed to read Docker container ID from /etc/hostname", e);
            return "unknown";
        }
    }

    public static Stream<String> safeArrayStream(String[]... arrays) {
        Stream<String> stream = Stream.empty();

        if (arrays != null) {
            for (String[] array : arrays) {
                stream = Stream.concat(stream, (array == null) ? Stream.empty() : Arrays.stream(array));
            }
        }

        return stream;
    }

    public static Stream<Object> safeArrayStream(Object[]... arrays) {
        Stream<Object> stream = Stream.empty();

        if (arrays != null) {
            for (Object[] array : arrays) {
                stream = Stream.concat(stream, (array == null) ? Stream.empty() : Arrays.stream(array));
            }
        }

        return stream;
    }

    public static void sleep(TimeUnit unit, long time) {
        try {
            Thread.sleep(unit.toMillis(time));
        } catch (InterruptedException exception) {
            log.error("Failed to sleep for " + time + " " + unit.name().toLowerCase() + "!", exception);
        }
    }

    @Nullable
    public static Guild getGuild(String guildId) {
        return NerdBotApp.getBot().getJDA().getGuildById(guildId);
    }

    @NotNull
    public static Guild getMainGuild() {
        return Objects.requireNonNull(NerdBotApp.getBot().getJDA().getGuildById(NerdBotApp.getBot().getConfig().getGuildId()));
    }

    public static File createTempFile(String fileName, String content) throws IOException {
        String dir = System.getProperty("java.io.tmpdir");
        File file = new File(dir + File.separator + fileName);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        log.info("Created temporary file " + file.getAbsolutePath());
        return file;
    }

    /**
     * Remove all reactions from a message by a user or bot
     *
     * @param reaction The {@link MessageReaction} to search for the list of users to remove
     * @param users    The {@link List list} of {@link User users} to remove the reaction from
     */
    public static int getReactionCountExcludingList(MessageReaction reaction, List<User> users) {
        return (int) reaction.retrieveUsers()
            .stream()
            .filter(user -> !users.contains(user))
            .count();
    }

    public static ForumTag getTagByName(ForumChannel forumChannel, String name) {
        return getTagByName(forumChannel, name, true);
    }

    public static ForumTag getTagByName(ForumChannel forumChannel, String name, boolean ignoreCase) {
        return forumChannel.getAvailableTags()
            .stream()
            .filter(forumTag -> (ignoreCase ? forumTag.getName().equalsIgnoreCase(name) : forumTag.getName().equals(name)))
            .findFirst()
            .orElseThrow();
    }

    public static boolean hasTagByName(ForumChannel forumChannel, String name) {
        return hasTagByName(forumChannel, name, true);
    }

    public static boolean hasTagByName(ForumChannel forumChannel, String name, boolean ignoreCase) {
        return forumChannel.getAvailableTags()
            .stream()
            .anyMatch(forumTag -> (ignoreCase ? forumTag.getName().equalsIgnoreCase(name) : forumTag.getName().equals(name)));
    }

    public static boolean hasTagByName(ThreadChannel threadChannel, String name) {
        return hasTagByName(threadChannel, name, true);
    }

    public static boolean hasTagByName(ThreadChannel threadChannel, String name, boolean ignoreCase) {
        return threadChannel.getAppliedTags()
            .stream()
            .anyMatch(forumTag -> (ignoreCase ? forumTag.getName().equalsIgnoreCase(name) : forumTag.getName().equals(name)));
    }

    public static Optional<Message> getFirstMessage(String threadId) {
        return getFirstMessage(NerdBotApp.getBot().getJDA().getThreadChannelById(threadId));
    }

    public static Optional<Message> getFirstMessage(ThreadChannel threadChannel) {
        if (threadChannel != null) {
            MessageHistory history = threadChannel.getHistoryFromBeginning(1).complete();
            return history.isEmpty() ? Optional.empty() : Optional.of(history.getRetrievedHistory().get(0));
        }

        return Optional.empty();
    }

    public static String formatSize(long size) {
        if (size <= 0) return "0";
        final String[] units = new String[]{"B", "kB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static String getFirstLine(Message message) {
        String firstLine = message.getContentRaw().split("\n")[0];

        if (firstLine.equals("")) {
            if (message.getEmbeds().get(0).getTitle() != null) {
                firstLine = message.getEmbeds().get(0).getTitle();
            } else {
                firstLine = "No Title Found";
            }
        }

        return (firstLine.length() > 30) ? firstLine.substring(0, 27) + "..." : firstLine;
    }

    public static Suggestion.ChannelType getThreadSuggestionType(ThreadChannel threadChannel) {
        return getForumSuggestionType(threadChannel.getParentChannel().asForumChannel());
    }

    public static Suggestion.ChannelType getForumSuggestionType(ForumChannel forumChannel) {
        SuggestionConfig suggestionConfig = NerdBotApp.getBot().getConfig().getSuggestionConfig();
        AlphaProjectConfig alphaProjectConfig = NerdBotApp.getBot().getConfig().getAlphaProjectConfig();
        String parentChannelId = forumChannel.getId();

        if (Util.safeArrayStream(alphaProjectConfig.getAlphaForumIds()).anyMatch(parentChannelId::equalsIgnoreCase)) {
            return Suggestion.ChannelType.ALPHA;
        } else if (Util.safeArrayStream(alphaProjectConfig.getProjectForumIds()).anyMatch(parentChannelId::equalsIgnoreCase)) {
            return Suggestion.ChannelType.PROJECT;
        } else if (parentChannelId.equals(suggestionConfig.getForumChannelId())) {
            return Suggestion.ChannelType.NORMAL;
        }

        Category parentCategory = forumChannel.getParentCategory();

        if (parentCategory != null) {
            return getChannelSuggestionTypeFromName(parentCategory.getName());
        }

        if (forumChannel.getName().toLowerCase().contains("alpha") || Arrays.stream(PROJECT_CHANNEL_NAMES).anyMatch(forumChannel.getName().toLowerCase()::contains)) {
            return getChannelSuggestionTypeFromName(forumChannel.getName());
        }

        return Suggestion.ChannelType.UNKNOWN;
    }

    // Only used for AlphaProjectConfig initialization and voice activity
    public static Suggestion.ChannelType getChannelSuggestionType(StandardGuildChannel channel) {
        return getChannelSuggestionTypeFromName(channel.getName());
    }

    public static Suggestion.ChannelType getChannelSuggestionTypeFromName(String name) {
        if (name.toLowerCase().contains("alpha")) {
            return Suggestion.ChannelType.ALPHA;
        } else if (Arrays.stream(PROJECT_CHANNEL_NAMES).anyMatch(name.toLowerCase()::contains)) {
            return Suggestion.ChannelType.PROJECT;
        } else {
            return Suggestion.ChannelType.NORMAL;
        }
    }

    public static JsonObject makeHttpRequest(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(String.format(url))).GET().build();
        String requestResponse;

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        requestResponse = response.body();

        return NerdBotApp.GSON.fromJson(requestResponse, JsonObject.class);
    }

    /***
     * Saves the image to a file
     * @return a file which can be shared
     * @throws IOException If the file cannot be saved
     */
    public static File toFile(BufferedImage imageToSave) throws IOException {
        File tempFile = File.createTempFile("image", ".png");
        ImageIO.write(imageToSave, "PNG", tempFile);
        return tempFile;
    }

    @Deprecated
    public static Optional<String> getScuffedMinecraftIGN(Member member) {
        // removes non-standard ascii characters from the discord nickname
        String plainUsername = member.getEffectiveName().trim().replaceAll("[^\u0000-\u007F]", "");
        String memberMCUsername = null;

        // checks if the member's username has flair
        if (!Pattern.matches(MINECRAFT_USERNAME_REGEX, plainUsername)) {
            // removes start and end characters ([example], {example}, |example| or (example)).
            // also strips spaces from the username
            plainUsername = plainUsername.replaceAll(SURROUND_REGEX, "").replace(" ", "");
            String[] splitUsername = plainUsername.split("[^a-zA-Z0-9_]");

            // gets the first item that matches the name constraints
            for (String item : splitUsername) {
                if (Pattern.matches(MINECRAFT_USERNAME_REGEX, item)) {
                    memberMCUsername = item;
                    break;
                }
            }
        } else {
            memberMCUsername = plainUsername.replace(" ", "");
        }

        return Optional.ofNullable(memberMCUsername);
    }

    public static MojangProfile getMojangProfile(String username) throws HttpException {
        String mojangUrl = String.format("https://api.mojang.com/users/profiles/minecraft/%s", username);
        String ashconUrl = String.format("https://api.ashcon.app/mojang/v2/user/%s", username);

        if (isUUID(username)) {
            mojangUrl = String.format("https://sessionserver.mojang.com/session/minecraft/profile/%s", username);
        }

        try {
            String body = sendRequestWithFallback(mojangUrl, ashconUrl);
            return NerdBotApp.GSON.fromJson(body, MojangProfile.class);
        } catch (IOException | InterruptedException exception) {
            throw new HttpException("Network error fetching profile for `" + username + "`", exception);
        } catch (Exception exception) {
            throw new HttpException("Failed to parse Mojang profile for `" + username + "`: " + exception.getMessage(), exception);
        }
    }

    @Nullable
    private static String sendRequestWithFallback(String primaryUrl, String fallbackUrl)
        throws IOException, InterruptedException, HttpException {
        HttpResponse<String> primary = getHttpResponse(primaryUrl);

        if (requestWasSuccessful(primary)) {
            return primary.body();
        }

        log.warn("Primary URL returned {}: {} (trying fallback URL)", primary.statusCode(), primary.body());
        HttpResponse<String> fallback = getHttpResponse(fallbackUrl);

        if (requestWasSuccessful(fallback)) {
            return fallback.body();
        }

        throw new HttpException(String.format("Both primary and fallback requests failed (primary: %d, fallback: %d)", primary.statusCode(), fallback.statusCode()));
    }

    @NotNull
    public static MojangProfile getMojangProfile(UUID uniqueId) throws HttpException {
        return getMojangProfile(uniqueId.toString());
    }

    private static boolean requestWasSuccessful(HttpResponse<?> response) {
        int code = response.statusCode();
        return code >= 200 && code < 300;
    }

    private static HttpResponse<String> getHttpResponse(String url, Pair<String, String>... headers) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).GET();
        Arrays.stream(headers).forEach(h -> builder.header(h.getLeft(), h.getRight()));

        HttpRequest request = builder.build();
        log.info("Sending HTTP request to {} with headers {}", url, Arrays.toString(headers));
        PrometheusMetrics.HTTP_REQUESTS_AMOUNT.labels(request.method(), url).inc();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    public static HypixelPlayerResponse getHypixelPlayer(UUID uniqueId) throws HttpException {
        String url = String.format("https://api.hypixel.net/player?uuid=%s", uniqueId);
        Summary.Timer requestTimer = PrometheusMetrics.HTTP_REQUEST_LATENCY.labels(url).startTimer();

        try {
            String hypixelApiKey = NerdBotApp.getHypixelAPIKey().map(UUID::toString).orElse("");
            return NerdBotApp.GSON.fromJson(getHttpResponse(url, Pair.of("API-Key", hypixelApiKey)).body(), HypixelPlayerResponse.class);
        } catch (Exception exception) {
            throw new HttpException("Unable to locate Hypixel Player for `" + uniqueId + "`", exception);
        } finally {
            requestTimer.observeDuration();
        }
    }

    public static boolean isUUID(String input) {
        return (input != null && !input.isEmpty()) && (input.matches(UUID_REGEX.pattern()) || input.matches(TRIMMED_UUID_REGEX.pattern()));
    }

    /**
     * Converts a string representation (with or without dashes) of a UUID to the {@link UUID} class.
     *
     * @param input unique id to convert.
     *
     * @return converted unique id.
     */
    public static UUID toUUID(String input) {
        if (!isUUID(input)) {
            throw new IllegalArgumentException("Not a valid UUID!");
        }

        if (input.contains("-")) {
            return UUID.fromString(input); // Already has hyphens
        }

        return UUID.fromString(input.replaceAll(ADD_UUID_HYPHENS_REGEX.pattern(), "$1-$2-$3-$4-$5"));
    }

    public static String getDisplayName(User user) {
        DiscordUserRepository discordUserRepository = NerdBotApp.getBot().getDatabase().getRepositoryManager().getRepository(DiscordUserRepository.class);
        DiscordUser discordUser = discordUserRepository.findById(user.getId());

        if (discordUser.isProfileAssigned()) {
            return discordUser.getMojangProfile().getUsername();
        } else {
            Guild guild = Util.getMainGuild();
            Member sbnMember = guild.retrieveMemberById(user.getId()).complete();

            if (sbnMember == null || sbnMember.getNickname() == null) {
                return user.getEffectiveName();
            }

            return sbnMember.getNickname();
        }
    }

    /**
     * Initializes a font.
     *
     * @param path The path to the font in the resources' folder.
     *
     * @return The initialized font.
     */
    @Nullable
    public static Font initFont(String path, float size) {
        Font font;
        try (InputStream fontStream = GeneratorCommands.class.getResourceAsStream(path)) {
            if (fontStream == null) {
                log.error("Couldn't initialize font: " + path);
                return null;
            }
            font = Font.createFont(Font.TRUETYPE_FONT, fontStream).deriveFont(size);
        } catch (IOException | FontFormatException exception) {
            log.error("Couldn't initialize font: " + path, exception);
            return null;
        }
        return font;
    }

    /**
     * Finds a matching value within a given set based on its name
     *
     * @param enumSet an array to search for the enum in
     * @param match   the value to find in the array
     *
     * @return returns the enum item or null if not found
     */
    @Nullable
    public static Enum<?> findValue(Enum<?>[] enumSet, String match) {
        for (Enum<?> enumItem : enumSet) {
            if (match.equalsIgnoreCase(enumItem.name()))
                return enumItem;
        }

        return null;
    }

    /**
     * Finds a matching emoji based on the given string
     * <br>
     * It can be a unicode emoji or a custom emoji ID
     *
     * @param emoji The emoji to find
     *
     * @return The emoji object
     */
    public static Optional<Emoji> getEmoji(String emoji) {
        if (EmojiManager.isEmoji(emoji)) {
            return Optional.of(Emoji.fromUnicode(emoji));
        }

        return EmojiCache.getEmojiById(emoji);
    }
}
