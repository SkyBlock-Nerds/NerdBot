package net.hypixel.nerdbot.app.generation.pack;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.data.Rarity;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.text.TextColorRemap;
import net.hypixel.nerdbot.discord.config.GeneratorConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers the resource packs declared in {@link GeneratorConfig.ResourcePackConfig} with the
 * image generator's {@link PackRepository} and answers pack-related queries for the generator
 * commands (pack option resolution, autocomplete choices and item searching).
 */
@Slf4j
public class ResourcePackService {

    /**
     * User-facing pack option value that always selects the built-in vanilla assets,
     * even when a default pack is configured.
     */
    public static final String VANILLA_OPTION = "vanilla";

    private final PackRepository packRepository;
    private final Map<PackId, List<String>> itemRefsByPack = new ConcurrentHashMap<>();
    private final Map<PackId, PackTheme> themesByPack = new ConcurrentHashMap<>();

    /**
     * A pack's configured tooltip theming: style refs keyed by lowercase rarity name plus the
     * parsed text color replacement table (null when the config declares none).
     */
    private record PackTheme(Map<String, String> stylesByRarityName, @Nullable TextColorRemap textColorRemap) {
        private static final PackTheme EMPTY = new PackTheme(Map.of(), null);
    }

    /**
     * The resolved default pack, cached once at registration so command invocations that omit the
     * pack option do not re-parse and re-validate the configured value on every call. Null means
     * "no (valid, registered) default pack" and therefore vanilla.
     */
    @Nullable
    private volatile PackId defaultPackId;

    public ResourcePackService(@NotNull PackRepository packRepository) {
        this.packRepository = Objects.requireNonNull(packRepository, "packRepository cannot be null");
    }

    /**
     * Registers every pack declared in the given configuration. A pack that fails to load is
     * logged and skipped so that one broken pack cannot prevent the bot (or the other packs)
     * from starting. Already-registered pack IDs are skipped, making this safe to call again on
     * the same instance.
     *
     * @param config The resource pack configuration, may be null when absent from the config file
     */
    public void registerConfiguredPacks(@Nullable GeneratorConfig.ResourcePackConfig config) {
        if (config == null) {
            log.info("No resource pack configuration found, only vanilla assets will be available");
            return;
        }

        List<GeneratorConfig.PackDefinition> packs = config.getPacks() == null ? List.of() : config.getPacks();

        for (GeneratorConfig.PackDefinition definition : packs) {
            if (definition == null) {
                log.warn("Skipping null resource pack entry in configuration");
                continue;
            }

            try {
                registerPack(definition);
            } catch (RuntimeException exception) {
                log.error("Failed to register resource pack '{}' from path '{}'", definition.getId(), definition.getPath(), exception);
            }
        }

        defaultPackId = resolveConfiguredDefaultPack(config.getDefaultPack());
    }

    /**
     * Resolves the value of a command's pack option to the {@link PackId} that should be passed
     * to the generator builders.
     *
     * @param userInput The raw pack option value, may be null when the user omitted the option
     *
     * @return The resolved {@link PackId}, or null for vanilla rendering
     *
     * @throws GeneratorException If the input is not a valid pack ID or references a pack that is not loaded
     */
    @Nullable
    public PackId resolvePackOption(@Nullable String userInput) {
        String input = userInput == null ? null : userInput.trim();

        if (input == null || input.isEmpty()) {
            return defaultPackId;
        }

        if (input.equalsIgnoreCase(VANILLA_OPTION) || input.equalsIgnoreCase(PackId.VANILLA.toString())) {
            return null;
        }

        PackId packId;

        try {
            packId = PackId.parse(input.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new GeneratorException("`" + input + "` is not a valid pack ID! Available packs: " + formatAvailablePacks());
        }

        if (!packRepository.registeredPacks().contains(packId)) {
            throw new GeneratorException("Pack `" + packId + "` is not loaded! Available packs: " + formatAvailablePacks());
        }

        return packId;
    }

    /**
     * Values offered by the pack option autocomplete: {@value VANILLA_OPTION} followed by every
     * registered pack ID in sorted order.
     */
    public List<String> packOptionChoices() {
        List<String> choices = new ArrayList<>();
        choices.add(VANILLA_OPTION);
        packRepository.registeredPacks().stream()
            .map(PackId::toString)
            .sorted()
            .forEach(choices::add);
        return choices;
    }

    /**
     * The distinct item refs (e.g. {@code hypixel_skyblock:item/jacob/cactus_knife}) across every
     * registered pack, sorted. Two packs sharing an asset namespace contribute each shared ref
     * only once.
     */
    public List<String> allItemRefs() {
        return itemRefsByPack.values().stream()
            .flatMap(List::stream)
            .distinct()
            .sorted()
            .toList();
    }

    /**
     * The item refs to suggest for a given pack option value, so autocomplete never offers a ref
     * the selected pack cannot resolve:
     * <ul>
     *     <li>a specific registered pack &rarr; only that pack's refs;</li>
     *     <li>{@value VANILLA_OPTION} (or {@code minecraft:minecraft}) &rarr; no pack refs;</li>
     *     <li>omitted with a configured default pack &rarr; the default pack's refs;</li>
     *     <li>omitted with no default (resolves to vanilla) &rarr; no pack refs;</li>
     *     <li>an unrecognised or half-typed value &rarr; all refs.</li>
     * </ul>
     * The omitted-with-no-default case returns no pack refs so that item autocomplete never
     * suggests a pack ref that would then fail to render against the vanilla default.
     *
     * @param packOption The raw pack option value from the same interaction, may be null
     */
    public List<String> itemRefsForOption(@Nullable String packOption) {
        return refsForPackOption(packOption, this::itemRefs, this::allItemRefs);
    }

    /**
     * Shared pack-option resolution for autocomplete suggestion lists (item refs, tooltip styles):
     * a specific registered pack yields its own refs, vanilla yields none, an omitted option
     * yields the default pack's refs (or none when vanilla is the default), and a half-typed or
     * unrecognised value yields everything rather than hiding all suggestions.
     */
    private List<String> refsForPackOption(@Nullable String packOption,
                                           java.util.function.Function<PackId, List<String>> perPack,
                                           java.util.function.Supplier<List<String>> allRefs) {
        String input = packOption == null ? null : packOption.trim();

        if (input != null && (input.equalsIgnoreCase(VANILLA_OPTION) || input.equalsIgnoreCase(PackId.VANILLA.toString()))) {
            return List.of();
        }

        Optional<PackId> selected = quietlyResolvePack(input);

        if (selected.isPresent()) {
            return perPack.apply(selected.get());
        }

        if (input == null || input.isEmpty()) {
            PackId defaultPack = defaultPackId;
            // Omitted option resolves to the configured default, or vanilla when there is none.
            // Vanilla renders no pack refs, so offer none rather than suggesting refs that would fail.
            return defaultPack != null ? perPack.apply(defaultPack) : List.of();
        }

        return allRefs.get();
    }

    /**
     * The item refs indexed for a single registered pack, or an empty list if the pack is unknown.
     */
    public List<String> itemRefs(@NotNull PackId packId) {
        return itemRefsByPack.getOrDefault(packId, List.of());
    }

    /**
     * The configured tooltip style ref for a rarity in a pack, or null when the pack, rarity,
     * or mapping is absent (the pack's default tooltip override then applies, if any).
     */
    @Nullable
    public String tooltipStyleFor(@Nullable PackId packId, @Nullable Rarity rarity) {
        if (packId == null || rarity == null) {
            return null;
        }

        PackTheme theme = themesByPack.get(packId);
        return theme == null ? null : theme.stylesByRarityName().get(rarity.getName().toLowerCase(Locale.ROOT));
    }

    /**
     * The configured text color replacement table for a pack, or null when the pack declares none.
     */
    @Nullable
    public TextColorRemap textColorRemapFor(@Nullable PackId packId) {
        if (packId == null) {
            return null;
        }

        PackTheme theme = themesByPack.get(packId);
        return theme == null ? null : theme.textColorRemap();
    }

    /**
     * The tooltip style refs to suggest for a given pack option value, following the same
     * resolution semantics as {@link #itemRefsForOption(String)}.
     */
    public List<String> tooltipStyleChoices(@Nullable String packOption) {
        return refsForPackOption(packOption, packRepository::tooltipStyles, this::allTooltipStyles);
    }

    private List<String> allTooltipStyles() {
        return packRepository.registeredPacks().stream()
            .map(packRepository::tooltipStyles)
            .flatMap(List::stream)
            .distinct()
            .sorted()
            .toList();
    }

    /**
     * Case-insensitively searches every registered pack for item refs containing the given query.
     */
    public List<String> searchItemRefs(@NotNull String query) {
        String loweredQuery = query.toLowerCase(Locale.ROOT);
        return allItemRefs().stream()
            .filter(ref -> ref.toLowerCase(Locale.ROOT).contains(loweredQuery))
            .toList();
    }

    private void registerPack(GeneratorConfig.PackDefinition definition) {
        if (definition.getId() == null || definition.getId().isBlank() || definition.getPath() == null || definition.getPath().isBlank()) {
            log.warn("Skipping resource pack with missing ID or path: {}", definition);
            return;
        }

        PackId packId = PackId.parse(definition.getId().toLowerCase(Locale.ROOT));

        if (packRepository.registeredPacks().contains(packId)) {
            log.info("Resource pack '{}' is already registered, skipping", packId);
            return;
        }

        Path path = Path.of(definition.getPath());

        if (!Files.exists(path)) {
            log.warn("Skipping resource pack '{}' because path '{}' does not exist", packId, path.toAbsolutePath());
            return;
        }

        // Parse the theme before opening the source so an invalid theme config skips the pack
        // without leaking a file handle. Silently registering the pack without its theme would
        // mask config typos behind wrong-looking renders.
        PackTheme theme;
        try {
            theme = parseTheme(definition);
        } catch (IllegalArgumentException exception) {
            log.error("Skipping resource pack '{}' because its theme configuration is invalid: {}",
                packId, exception.getMessage());
            return;
        }

        PackLimits limits = PackLimits.fromSystemProperties();
        PackSource source = Files.isDirectory(path) ? PackSource.directory(path, limits) : PackSource.zip(path, limits);

        // Index item refs while we still own the source; the repository takes ownership on
        // successful registration (and closes the source on failure).
        List<String> itemRefs = indexItemRefs(packId, source);
        packRepository.register(packId.toString(), source, limits);
        itemRefsByPack.put(packId, itemRefs);
        themesByPack.put(packId, theme);
        warnAboutMissingConfiguredStyles(packId, theme);

        log.info("Registered resource pack '{}' from '{}' with {} item definitions and {} tooltip styles",
            packId, path.toAbsolutePath(), itemRefs.size(), packRepository.tooltipStyles(packId).size());
    }

    /**
     * Parses and validates a pack definition's theme configuration. Rarity keys are normalized
     * to lowercase and must name a known rarity; remap colors must be #RRGGBB hex strings.
     *
     * @throws IllegalArgumentException on any invalid entry
     */
    private static PackTheme parseTheme(GeneratorConfig.PackDefinition definition) {
        Map<String, String> styles = new HashMap<>();
        Map<String, String> configuredStyles = definition.getTooltipStyles() == null ? Map.of() : definition.getTooltipStyles();

        for (Map.Entry<String, String> entry : configuredStyles.entrySet()) {
            String rarityName = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
            String styleRef = entry.getValue() == null ? "" : entry.getValue().trim();

            if (rarityName.isEmpty() || styleRef.isEmpty()) {
                throw new IllegalArgumentException("tooltipStyles entries need a rarity name and a style ref, got '"
                    + entry.getKey() + "' -> '" + entry.getValue() + "'");
            }

            if (Rarity.byName(rarityName) == null) {
                throw new IllegalArgumentException("tooltipStyles references unknown rarity '" + rarityName
                    + "'; known rarities: " + String.join(", ", Rarity.getRarityNames()));
            }

            if (styles.putIfAbsent(rarityName, styleRef) != null) {
                throw new IllegalArgumentException("Duplicate tooltipStyles entry for rarity '" + rarityName + "'");
            }
        }

        Map<String, String> configuredRemap = definition.getTextColorRemap() == null ? Map.of() : definition.getTextColorRemap();
        TextColorRemap remap = null;

        if (!configuredRemap.isEmpty()) {
            TextColorRemap.Builder builder = TextColorRemap.builder();
            configuredRemap.forEach((from, to) ->
                builder.remap(parseHexColor(from, "textColorRemap key"), parseHexColor(to, "textColorRemap value")));
            remap = builder.build();
        }

        return styles.isEmpty() && remap == null ? PackTheme.EMPTY : new PackTheme(Map.copyOf(styles), remap);
    }

    private static int parseHexColor(@Nullable String value, String description) {
        String trimmed = value == null ? "" : value.trim();

        if (!trimmed.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException(description + " must be a #RRGGBB hex color, got '" + value + "'");
        }

        return Integer.parseInt(trimmed.substring(1), 16);
    }

    /**
     * Startup visibility for configured styles the pack does not actually define. Registration
     * proceeds (the pack file may gain the style later); rendering that rarity fails loudly in
     * the library until the pack or the config changes.
     */
    private void warnAboutMissingConfiguredStyles(PackId packId, PackTheme theme) {
        List<String> packStyles = packRepository.tooltipStyles(packId);
        theme.stylesByRarityName().forEach((rarityName, styleRef) -> {
            if (!packStyles.contains(styleRef)) {
                log.warn("Pack '{}' does not define configured tooltip style '{}' (rarity '{}')",
                    packId, styleRef, rarityName);
            }
        });
    }

    private List<String> indexItemRefs(PackId packId, PackSource source) {
        try {
            return source.list("assets/").stream()
                .map(ResourcePackService::toItemRef)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        } catch (RuntimeException exception) {
            log.warn("Failed to index item refs for pack '{}', autocomplete will not include its items", packId, exception);
            return List.of();
        }
    }

    /**
     * Maps a pack asset path like {@code assets/<namespace>/items/<path>.json} to the item ref
     * {@code <namespace>:<path>} used by the generators, or null for any other asset path.
     */
    @Nullable
    private static String toItemRef(String assetPath) {
        if (!assetPath.startsWith("assets/") || !assetPath.endsWith(".json")) {
            return null;
        }

        String withoutPrefix = assetPath.substring("assets/".length());
        int namespaceEnd = withoutPrefix.indexOf('/');

        if (namespaceEnd <= 0 || !withoutPrefix.startsWith("items/", namespaceEnd + 1)) {
            return null;
        }

        String namespace = withoutPrefix.substring(0, namespaceEnd);
        String itemPath = withoutPrefix.substring(namespaceEnd + 1 + "items/".length(), withoutPrefix.length() - ".json".length());

        if (itemPath.isEmpty()) {
            return null;
        }

        return namespace + ":" + itemPath;
    }

    /**
     * Resolves a configured default pack value to a registered {@link PackId}, logging (once, at
     * registration time) if it is malformed or not registered. Returns null for vanilla.
     */
    @Nullable
    private PackId resolveConfiguredDefaultPack(@Nullable String configuredDefault) {
        if (configuredDefault == null || configuredDefault.isBlank()) {
            return null;
        }

        String trimmed = configuredDefault.trim();
        PackId packId;

        try {
            packId = PackId.parse(trimmed.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            log.warn("Configured default pack '{}' is not a valid pack ID, commands will fall back to vanilla", trimmed);
            return null;
        }

        if (!packRepository.registeredPacks().contains(packId)) {
            log.warn("Configured default pack '{}' is not registered, commands will fall back to vanilla", packId);
            return null;
        }

        return packId;
    }

    /**
     * Parses and validates a pack option without throwing, for autocomplete paths where a
     * half-typed or unknown value should simply not match rather than raise an error.
     */
    private Optional<PackId> quietlyResolvePack(@Nullable String input) {
        if (input == null || input.isEmpty()) {
            return Optional.empty();
        }

        try {
            PackId packId = PackId.parse(input.toLowerCase(Locale.ROOT));
            return packRepository.registeredPacks().contains(packId) ? Optional.of(packId) : Optional.empty();
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String formatAvailablePacks() {
        return String.join(", ", packOptionChoices());
    }
}
