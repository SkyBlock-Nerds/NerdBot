package net.hypixel.nerdbot.app.generation.pack.update;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.pack.PackFormatRange;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLimits;
import net.aerh.imagegenerator.pack.PackSource;
import net.aerh.imagegenerator.tools.pack.PackExpectations;
import net.aerh.imagegenerator.tools.pack.PackReloadResult;
import net.aerh.imagegenerator.tools.pack.ResourcePackService;
import net.hypixel.nerdbot.app.config.GeneratorConfig;
import net.hypixel.nerdbot.app.generation.pack.PackConfigMapper;
import net.hypixel.nerdbot.marmalade.exception.DownloadRejectedException;
import net.hypixel.nerdbot.marmalade.exception.HttpException;
import net.hypixel.nerdbot.marmalade.functional.Result;
import net.hypixel.nerdbot.marmalade.http.DownloadResult;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Checks a pack's release source for a newer version, downloads, validates and applies it, and rolls back on request.
 * One check or rollback runs at a time across all packs; a second caller gets {@link PackUpdateOutcome.Busy}.
 * Every outcome except {@code Busy} is reported to the listener and kept as the pack's last outcome;
 * {@code Busy} is only returned to the caller.
 */
@Slf4j
public class PackUpdater {

    private static final String CONFIGURED_DEPLOY_ID = "configured";

    private final ResourcePackService packService;
    private final HypixelPackApiClient apiClient;
    private final PackDownloader downloader;
    private final PackCacheStore store;
    private final GeneratorConfig.AutoUpdateSettings settings;
    private final PackUpdateListener listener;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Integer> consecutiveTransientFailures = new ConcurrentHashMap<>();
    private final Map<String, RecordedOutcome> lastOutcomes = new ConcurrentHashMap<>();

    public record RecordedOutcome(PackUpdateOutcome outcome, long atEpochMs) {
    }

    public PackUpdater(ResourcePackService packService, HypixelPackApiClient apiClient, PackDownloader downloader,
                       PackCacheStore store, GeneratorConfig.AutoUpdateSettings settings, PackUpdateListener listener, Clock clock) {
        this.packService = packService;
        this.apiClient = apiClient;
        this.downloader = downloader;
        this.store = store;
        this.settings = settings;
        this.listener = listener;
        this.clock = clock;
    }

    public PackCacheStore store() {
        return store;
    }

    public Optional<RecordedOutcome> lastOutcome(String packId) {
        return Optional.ofNullable(lastOutcomes.get(packId));
    }

    /**
     * The packs eligible for automatic updates: autoUpdate enabled, id and path present, a valid
     * pack id, a minItemRatio between 0 and 1, a valid update source, and a lowercase id and an
     * update source that no other eligible pack also uses. Every excluded pack is logged with the reason; packs sharing an id
     * or an update source are all excluded, since updating either would be a guess.
     */
    public static List<GeneratorConfig.PackDefinition> enabledDefinitions(@Nullable GeneratorConfig.ResourcePackConfig config) {
        return enabledDefinitions(config, false);
    }

    /**
     * Same as {@link #enabledDefinitions(GeneratorConfig.ResourcePackConfig)}, but when
     * {@code reportProblems} is true the reasons packs are excluded are logged at error instead of
     * debug. Callers pass true once at boot so the repeated scheduled and command calls stay quiet.
     */
    public static List<GeneratorConfig.PackDefinition> enabledDefinitions(@Nullable GeneratorConfig.ResourcePackConfig config,
                                                                          boolean reportProblems) {
        if (config == null || config.getPacks() == null) {
            return List.of();
        }

        List<GeneratorConfig.PackDefinition> candidates = config.getPacks().stream()
            .filter(definition -> definition != null && definition.getAutoUpdate() != null && definition.getAutoUpdate().isEnabled())
            .filter(definition -> isPresent(definition.getId()) && isPresent(definition.getPath()))
            .filter(definition -> hasValidSettings(definition, reportProblems))
            .toList();

        Map<String, Long> packIdCounts = candidates.stream()
            .collect(Collectors.groupingBy(PackUpdater::packIdOf, Collectors.counting()));
        Map<String, Long> sourceCounts = candidates.stream()
            .collect(Collectors.groupingBy(definition -> ReleaseSources.identity(definition.getAutoUpdate()), Collectors.counting()));

        List<GeneratorConfig.PackDefinition> eligible = new ArrayList<>();
        for (GeneratorConfig.PackDefinition definition : candidates) {
            if (packIdCounts.get(packIdOf(definition)) > 1) {
                logProblem(reportProblems, "Pack '{}' has the same id as another pack once lowercased, so neither is updated automatically",
                    definition.getId());
                continue;
            }
            if (sourceCounts.get(ReleaseSources.identity(definition.getAutoUpdate())) > 1) {
                logProblem(reportProblems, "Pack '{}' reads the same update source ({}) as another pack, so neither is updated automatically",
                    definition.getId(), ReleaseSources.identity(definition.getAutoUpdate()));
                continue;
            }
            eligible.add(definition);
        }
        return List.copyOf(eligible);
    }

    /** Whether the pack's id parses, its minItemRatio is usable and its update source is valid, logging the reason when not. */
    private static boolean hasValidSettings(GeneratorConfig.PackDefinition definition, boolean reportProblems) {
        try {
            PackId.parse(packIdOf(definition));
        } catch (IllegalArgumentException e) {
            logProblem(reportProblems, "Pack '{}' has an invalid id ({}), so it is not updated automatically", definition.getId(), e.getMessage());
            return false;
        }

        double minItemRatio = definition.getAutoUpdate().getMinItemRatio();
        if (Double.isNaN(minItemRatio) || minItemRatio < 0 || minItemRatio > 1) {
            logProblem(reportProblems, "Pack '{}' has minItemRatio {} outside 0 to 1, so it is not updated automatically", definition.getId(), minItemRatio);
            return false;
        }

        String sourceProblem = ReleaseSources.problem(definition.getAutoUpdate());
        if (sourceProblem != null) {
            logProblem(reportProblems, "Pack '{}' has an invalid update source ({}), so it is not updated automatically",
                definition.getId(), sourceProblem);
            return false;
        }
        return true;
    }

    private static void logProblem(boolean reportProblems, String message, Object... args) {
        if (reportProblems) {
            log.error(message, args);
        } else {
            log.debug(message, args);
        }
    }

    public static String packIdOf(GeneratorConfig.PackDefinition definition) {
        return PackConfigMapper.normalisedId(definition.getId());
    }

    /** Runs one update check for a pack, unless another check or rollback is running. */
    public PackUpdateOutcome checkForUpdate(GeneratorConfig.PackDefinition definition) {
        return runExclusively(packIdOf(definition), true, () -> doCheck(definition));
    }

    /** Swaps back to the previous pack and rejects the current one, unless another run is in progress. */
    public PackUpdateOutcome rollback(GeneratorConfig.PackDefinition definition) {
        return runExclusively(packIdOf(definition), false, () -> doRollback(definition));
    }

    /**
     * Runs {@code work} under the lock and records its outcome before releasing it. Only checks
     * ({@code isCheck}) count or reset consecutive transient failures; a rollback never does.
     */
    private PackUpdateOutcome runExclusively(String packId, boolean isCheck, Supplier<PackUpdateOutcome> work) {
        if (!lock.tryLock()) {
            return new PackUpdateOutcome.Busy();
        }

        PackUpdateOutcome outcome;
        try {
            try {
                outcome = work.get();
            } catch (RuntimeException e) {
                if (isCheck) {
                    outcome = transientFailure(packId, "unexpected error: " + describe(e), e);
                } else {
                    log.warn("Rollback of pack '{}' failed unexpectedly", packId, e);
                    outcome = new PackUpdateOutcome.Unavailable("unexpected error: " + describe(e));
                }
            }

            if (isCheck && !(outcome instanceof PackUpdateOutcome.TransientFailure)) {
                consecutiveTransientFailures.remove(packId);
            }
            lastOutcomes.put(packId, new RecordedOutcome(outcome, clock.millis()));
        } finally {
            lock.unlock();
        }

        try {
            listener.onOutcome(packId, outcome);
        } catch (RuntimeException e) {
            log.error("Pack update listener failed for '{}'", packId, e);
        }
        return outcome;
    }

    private PackUpdateOutcome doCheck(GeneratorConfig.PackDefinition definition) {
        String packId = packIdOf(definition);
        GeneratorConfig.PackAutoUpdate autoUpdate = definition.getAutoUpdate();

        Result<PackRelease, HttpException> fetched = ReleaseSources.forDefinition(apiClient, autoUpdate).latest();
        if (fetched instanceof Result.Failure<PackRelease, HttpException> failure) {
            return transientFailure(packId, failure.error().getMessage(), failure.error());
        }
        PackRelease release = fetched.orElseThrow();

        int topFormat = release.versions().stream().mapToInt(PackReleaseVersion::packFormat).max().orElseThrow();
        List<PackReleaseVersion> atTop = release.versions().stream()
            .filter(version -> version.packFormat() == topFormat)
            .toList();
        if (atTop.size() > 1) {
            return transientFailure(packId, "the latest release lists " + atTop.size()
                + " versions with format " + topFormat + ", refusing to guess", null);
        }
        PackReleaseVersion selected = atTop.getFirst();
        String deployKey = PackState.deployKey(release.deployId(), selected.packFormat());
        String label = selected.label(release.deployId());

        PackState.Slot slot = store.slot(packId);
        if (isLive(slot.current(), deployKey, selected)) {
            return new PackUpdateOutcome.UpToDate(label, false);
        }

        if (slot.current() == null && selected.sha1() != null) {
            Optional<Path> configured = configuredZipMatching(packId, definition, selected.sha1());
            if (configured.isPresent()) {
                return adoptConfiguredPack(packId, configured.get(), release, selected);
            }
        }

        String fingerprint = RejectionFingerprint.of(settings, autoUpdate);
        Optional<PackState.RejectedHash> rejection = slot.rejection(selected.sha1(), deployKey, fingerprint);
        if (rejection.isPresent()) {
            log.debug("Pack '{}' version {} was rejected before ({}), skipping", packId, label, rejection.get().reason());
            return new PackUpdateOutcome.UpToDate(label, true);
        }

        if (slot.current() == null && slot.previous() == null) {
            seedConfiguredPackAsPrevious(packId, definition);
        }

        return downloadAndApply(packId, definition, release, selected, deployKey, label, fingerprint);
    }

    /**
     * Whether {@code current} is this version: the same SHA-1 when the source publishes one,
     * otherwise the same deploy id and format.
     */
    private static boolean isLive(@Nullable PackState.AppliedPack current, String deployKey, PackReleaseVersion selected) {
        if (current == null) {
            return false;
        }
        if (selected.sha1() != null) {
            return current.sha1().equals(selected.sha1());
        }
        return deployKey.equals(current.deployKey());
    }

    /** The configured pack file, only when it is a regular file whose SHA-1 is {@code sha1}. */
    private static Optional<Path> configuredZipMatching(String packId, GeneratorConfig.PackDefinition definition, String sha1) {
        try {
            Path configured = Path.of(definition.getPath());
            return PackCacheStore.fileMatches(configured, sha1) ? Optional.of(configured) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            // RuntimeException covers InvalidPathException from a bad configured path
            log.debug("Could not compare configured pack '{}' with the latest version: {}", packId, describe(e));
        }
        return Optional.empty();
    }

    /**
     * Records the configured pack as the current one when it already is the latest version. It is
     * live already, so nothing is downloaded or reloaded. Only possible when the source publishes a SHA-1.
     */
    private PackUpdateOutcome adoptConfiguredPack(String packId, Path configured, PackRelease release, PackReleaseVersion selected) {
        Path cached;
        try {
            cached = store.importCopy(configured, selected.sha1());
        } catch (IOException e) {
            return transientFailure(packId, "could not copy the configured pack into the cache: " + describe(e), e);
        }

        store.recordApplied(packId, new PackState.AppliedPack(selected.packFormat(), selected.sha1(), release.deployId(),
            cached.getFileName().toString(), clock.millis()));
        log.info("Configured pack '{}' is already the latest format {} ({}), recorded it as current",
            packId, selected.packFormat(), selected.sha1());
        return new PackUpdateOutcome.UpToDate(selected.sha1(), false);
    }

    /** A cached zip and its SHA-1. */
    private record CachedZip(Path zip, String sha1) {
    }

    /**
     * A zip for this version that is already in the cache: by SHA-1 when the source publishes one,
     * otherwise the current or previous pack with the same deploy id and format, if its file is intact.
     */
    private Optional<CachedZip> reusableZip(String packId, String deployKey, PackReleaseVersion selected) {
        if (selected.sha1() != null) {
            return store.cachedZip(selected.sha1()).map(zip -> new CachedZip(zip, selected.sha1()));
        }

        PackState.Slot slot = store.slot(packId);
        for (PackState.AppliedPack entry : Arrays.asList(slot.current(), slot.previous())) {
            if (entry != null && deployKey.equals(entry.deployKey())) {
                Optional<Path> zip = store.verifiedZip(entry);
                if (zip.isPresent()) {
                    return Optional.of(new CachedZip(zip.get(), entry.sha1()));
                }
            }
        }
        return Optional.empty();
    }

    private PackUpdateOutcome downloadAndApply(String packId, GeneratorConfig.PackDefinition definition,
                                               PackRelease release, PackReleaseVersion selected, String deployKey,
                                               String label, String fingerprint) {
        Path zip;
        String sha1;
        Optional<CachedZip> reusable = reusableZip(packId, deployKey, selected);
        if (reusable.isPresent()) {
            log.info("Pack '{}' version {} is already in the cache, skipping the download", packId, label);
            zip = reusable.get().zip();
            sha1 = reusable.get().sha1();
        } else {
            // Partial downloads are named by what is known before downloading
            String partKey = selected.sha1() != null ? selected.sha1() : deployKey.replace('/', '-');
            Result<DownloadResult, HttpException> downloaded = downloader.download(selected.url(), store.partPath(partKey));
            if (downloaded instanceof Result.Failure<DownloadResult, HttpException> failure) {
                HttpException error = failure.error();
                if (error instanceof DownloadRejectedException) {
                    return reject(packId, label, deployKey, selected.packFormat(), error.getMessage(), fingerprint);
                }
                return transientFailure(packId, "could not download format " + selected.packFormat() + ": " + error.getMessage(), error);
            }

            String actualSha1 = downloaded.orElseThrow().sha1Hex();
            if (selected.sha1() != null && !actualSha1.equals(selected.sha1())) {
                store.deletePart(partKey);
                return reject(packId, label, deployKey, selected.packFormat(),
                    "the download's SHA-1 is " + actualSha1 + " but the source published " + selected.sha1(), fingerprint);
            }

            try {
                zip = store.promotePart(partKey, actualSha1);
            } catch (IOException e) {
                store.deletePart(partKey);
                return transientFailure(packId, "could not move the download into the cache: " + describe(e), e);
            }
            sha1 = actualSha1;
        }

        PackReloadResult result;
        try {
            result = packService.reloadPack(
                PackConfigMapper.toDefinition(definition, zip.toString()),
                zip,
                expectationsFor(definition, selected.packFormat(), definition.getAutoUpdate().getMinItemRatio())
            );
        } catch (RuntimeException e) {
            store.deleteIfUnreferenced(sha1);
            return transientFailure(packId, "the pack could not be reloaded: " + describe(e), e);
        }

        PackReloadResult.Applied applied;
        switch (result) {
            case PackReloadResult.Rejected rejected -> {
                store.deleteIfUnreferenced(sha1);
                return reject(packId, sha1, deployKey, selected.packFormat(), rejected.reason(), fingerprint);
            }
            case PackReloadResult.NotAttempted notAttempted -> {
                // Our configuration is at fault, not the pack: never remember the version as rejected
                store.deleteIfUnreferenced(sha1);
                return transientFailure(packId, "the pack was not tried: " + notAttempted.reason(), null);
            }
            case PackReloadResult.Applied success -> applied = success;
        }

        PackState.AppliedPack from = store.slot(packId).current();
        PackState.AppliedPack to = new PackState.AppliedPack(selected.packFormat(), sha1, release.deployId(),
            zip.getFileName().toString(), clock.millis());
        store.recordApplied(packId, to);
        store.cleanup();
        log.info("Applied pack '{}' format {} ({}, deploy {})", packId, to.packFormat(), to.sha1(), to.deployId());
        return new PackUpdateOutcome.Applied(from, to, applied.itemCount(), applied.previousItemCount());
    }

    private PackUpdateOutcome doRollback(GeneratorConfig.PackDefinition definition) {
        String packId = packIdOf(definition);
        PackState.Slot slot = store.slot(packId);

        if (slot.current() == null) {
            return new PackUpdateOutcome.Unavailable("no automatic update has been applied yet, so the configured pack is already live");
        }
        if (slot.previous() == null) {
            return new PackUpdateOutcome.Unavailable("there is no previous pack to roll back to");
        }
        if (slot.previous().sha1().equals(slot.current().sha1())) {
            return new PackUpdateOutcome.Unavailable("the previous pack is the same as the live one");
        }

        Optional<Path> previousZip = store.verifiedZip(slot.previous());
        if (previousZip.isEmpty()) {
            return new PackUpdateOutcome.Unavailable("the previous pack's file is missing or its SHA-1 no longer matches");
        }

        PackReloadResult result;
        try {
            result = packService.reloadPack(
                PackConfigMapper.toDefinition(definition, previousZip.get().toString()),
                previousZip.get(),
                // The previous pack was live before, and the ratio would be measured against the pack being rolled away from
                expectationsFor(definition, slot.previous().packFormat(), 0)
            );
        } catch (RuntimeException e) {
            log.warn("Rollback of pack '{}' could not load the previous pack", packId, e);
            return new PackUpdateOutcome.Unavailable("the previous pack could not be loaded: " + describe(e));
        }

        switch (result) {
            case PackReloadResult.Rejected rejected -> {
                return new PackUpdateOutcome.Unavailable("the previous pack failed validation: " + rejected.reason());
            }
            case PackReloadResult.NotAttempted notAttempted -> {
                return new PackUpdateOutcome.Unavailable("the previous pack was not tried: " + notAttempted.reason());
            }
            case PackReloadResult.Applied ignored -> {
                // Fall through to record the rollback
            }
        }

        PackState.AppliedPack from = slot.current();
        // No fingerprint: an admin's rollback holds whatever the settings become
        store.recordRolledBack(packId, new PackState.RejectedHash(from.sha1(), from.packFormat(), "rolled back by an admin",
            clock.millis(), null, from.deployKey()));
        log.info("Rolled pack '{}' back from {} to {}", packId, from.sha1(), slot.previous().sha1());
        return new PackUpdateOutcome.RolledBack(from, slot.previous());
    }

    /**
     * Copies the configured pack zip into the cache and records it as the rollback target, so the
     * very first automatic update can be undone. Failure only means rollback is unavailable.
     */
    private void seedConfiguredPackAsPrevious(String packId, GeneratorConfig.PackDefinition definition) {
        try {
            Path configured = Path.of(definition.getPath());
            if (!Files.isRegularFile(configured)) {
                log.warn("Pack '{}' is configured from a directory or missing file '{}', so the first update cannot be rolled back", packId, configured);
                return;
            }

            String sha1 = PackCacheStore.sha1Of(configured);
            Path cached = store.importCopy(configured, sha1);

            PackLimits limits = PackLimits.fromSystemProperties();
            Optional<PackFormatRange> format;
            try (PackSource source = PackSource.zip(cached, limits)) {
                format = PackFormatRange.read(source);
            }

            if (format.isEmpty()) {
                log.warn("Configured pack '{}' declares no pack format, so the first update cannot be rolled back", packId);
                store.deleteIfUnreferenced(sha1);
                return;
            }

            store.recordPrevious(packId, new PackState.AppliedPack(format.get().max(), sha1, CONFIGURED_DEPLOY_ID,
                cached.getFileName().toString(), clock.millis()));
        } catch (IOException | RuntimeException e) {
            // RuntimeException covers InvalidPathException from a bad configured path
            log.warn("Could not keep a copy of configured pack '{}', so the first update cannot be rolled back", packId, e);
        }
    }

    private static PackExpectations expectationsFor(GeneratorConfig.PackDefinition definition, int packFormat, double minItemRatio) {
        return new PackExpectations(packFormat, minItemRatio, definition.getAutoUpdate().getSampleItems());
    }

    private PackUpdateOutcome reject(String packId, String sha1, String deployKey, int packFormat, String reason, String fingerprint) {
        store.recordRejected(packId, new PackState.RejectedHash(sha1, packFormat, reason, clock.millis(), fingerprint, deployKey));
        log.warn("Rejected pack '{}' format {} ({}): {}", packId, packFormat, sha1, reason);
        return new PackUpdateOutcome.Rejected(sha1, packFormat, reason);
    }

    private PackUpdateOutcome transientFailure(String packId, String reason, @Nullable Throwable cause) {
        int failures = consecutiveTransientFailures.merge(packId, 1, Integer::sum);
        log.warn("Pack '{}' update check failed ({} in a row): {}", packId, failures, reason);
        return new PackUpdateOutcome.TransientFailure(reason, cause, failures);
    }

    /** The throwable's message, or its simple class name when it has none. */
    private static String describe(Throwable throwable) {
        return throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
    }

    private static boolean isPresent(@Nullable String value) {
        return value != null && !value.isBlank();
    }
}
