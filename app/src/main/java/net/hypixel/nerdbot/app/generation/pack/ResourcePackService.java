package net.hypixel.nerdbot.app.generation.pack;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackRepository;
import net.aerh.imagegenerator.pack.PackSource;
import net.hypixel.nerdbot.discord.config.GeneratorConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
     *     <li>omitted with no default, or an unrecognised/half-typed value &rarr; all refs.</li>
     * </ul>
     *
     * @param packOption The raw pack option value from the same interaction, may be null
     */
    public List<String> itemRefsForOption(@Nullable String packOption) {
        String input = packOption == null ? null : packOption.trim();

        if (input != null && (input.equalsIgnoreCase(VANILLA_OPTION) || input.equalsIgnoreCase(PackId.VANILLA.toString()))) {
            return List.of();
        }

        Optional<PackId> selected = quietlyResolvePack(input);

        if (selected.isPresent()) {
            return itemRefs(selected.get());
        }

        if (input == null || input.isEmpty()) {
            PackId defaultPack = defaultPackId;
            return defaultPack != null ? itemRefs(defaultPack) : allItemRefs();
        }

        // A half-typed or unrecognised pack value: fall back to every ref rather than hiding them all
        return allItemRefs();
    }

    /**
     * The item refs indexed for a single registered pack, or an empty list if the pack is unknown.
     */
    public List<String> itemRefs(@NotNull PackId packId) {
        return itemRefsByPack.getOrDefault(packId, List.of());
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

        PackLimits limits = PackLimits.fromSystemProperties();
        PackSource source = Files.isDirectory(path) ? PackSource.directory(path, limits) : PackSource.zip(path, limits);

        // Index item refs while we still own the source; the repository takes ownership on
        // successful registration (and closes the source on failure).
        List<String> itemRefs = indexItemRefs(packId, source);
        packRepository.register(packId.toString(), source, limits);
        itemRefsByPack.put(packId, itemRefs);

        log.info("Registered resource pack '{}' from '{}' with {} item definitions", packId, path.toAbsolutePath(), itemRefs.size());
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
