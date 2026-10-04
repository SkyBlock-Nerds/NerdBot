package net.hypixel.nerdbot.app.generation.pack.update;

import net.hypixel.nerdbot.app.config.GeneratorConfig;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Turns a pack's autoUpdate config into the {@link ReleaseSource} that reports its new versions. */
public final class ReleaseSources {

    public static final String HYPIXEL_API = "hypixel-api";
    public static final String DEPLOY_INDEX = "deploy-index";
    static final String DEPLOY_ID_PLACEHOLDER = "{deployId}";
    static final String FORMAT_PLACEHOLDER = "{format}";
    private static final String SAMPLE_DEPLOY_ID = "00000000-0000-0000-0000-000000000000";

    private ReleaseSources() {
    }

    /** The configured source type in lowercase, {@value #HYPIXEL_API} when no source or type is set. */
    public static String typeOf(GeneratorConfig.PackAutoUpdate autoUpdate) {
        GeneratorConfig.PackSourceSettings source = autoUpdate.getSource();
        if (source == null || isBlank(source.getType())) {
            return HYPIXEL_API;
        }
        return source.getType().trim().toLowerCase(Locale.ROOT);
    }

    /** Why the configured source cannot be used, or null when it can. */
    @Nullable
    public static String problem(GeneratorConfig.PackAutoUpdate autoUpdate) {
        return switch (typeOf(autoUpdate)) {
            case HYPIXEL_API -> isBlank(autoUpdate.getHypixelPackId()) ? "hypixelPackId is empty" : null;
            case DEPLOY_INDEX -> deployIndexProblem(autoUpdate.getSource());
            default -> "unknown source type '" + autoUpdate.getSource().getType() + "', expected "
                + HYPIXEL_API + " or " + DEPLOY_INDEX;
        };
    }

    @Nullable
    private static String deployIndexProblem(GeneratorConfig.PackSourceSettings source) {
        String url = trimmed(source.getUrl());
        if (url.isEmpty()) {
            return "source.url is empty";
        }
        try {
            URI parsed = new URI(url);
            if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null) {
                return "source.url must be an https URL";
            }
        } catch (URISyntaxException e) {
            return "source.url is not a valid URL: " + e.getMessage();
        }

        String template = trimmed(source.getDownloadUrlTemplate());
        if (!template.contains(DEPLOY_ID_PLACEHOLDER) || !template.contains(FORMAT_PLACEHOLDER)) {
            return "source.downloadUrlTemplate must contain " + DEPLOY_ID_PLACEHOLDER + " and " + FORMAT_PLACEHOLDER;
        }
        try {
            new URI(DeployIndexReleaseSource.downloadUrl(template, SAMPLE_DEPLOY_ID, 1));
        } catch (URISyntaxException e) {
            return "source.downloadUrlTemplate does not form a valid URL: " + e.getMessage();
        }
        return null;
    }

    /**
     * What the source reads, so two packs reading the same source can be told apart. Only call it
     * for config that passed {@link #problem}.
     */
    public static String identity(GeneratorConfig.PackAutoUpdate autoUpdate) {
        if (DEPLOY_INDEX.equals(typeOf(autoUpdate))) {
            return DEPLOY_INDEX + ":" + trimmed(autoUpdate.getSource().getUrl());
        }
        return HYPIXEL_API + ":" + trimmed(autoUpdate.getHypixelPackId());
    }

    /** The source settings that decide which zip gets downloaded, for the rejection fingerprint. */
    public static String fingerprintPart(GeneratorConfig.PackAutoUpdate autoUpdate) {
        if (DEPLOY_INDEX.equals(typeOf(autoUpdate))) {
            return identity(autoUpdate) + "|" + trimmed(autoUpdate.getSource().getDownloadUrlTemplate());
        }
        return identity(autoUpdate);
    }

    /** Builds the source for a pack whose config passed {@link #problem}. */
    public static ReleaseSource forDefinition(HypixelPackApiClient apiClient, GeneratorConfig.PackAutoUpdate autoUpdate) {
        String problem = problem(autoUpdate);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        if (DEPLOY_INDEX.equals(typeOf(autoUpdate))) {
            return new DeployIndexReleaseSource(trimmed(autoUpdate.getSource().getUrl()),
                trimmed(autoUpdate.getSource().getDownloadUrlTemplate()));
        }
        return new HypixelApiReleaseSource(apiClient, trimmed(autoUpdate.getHypixelPackId()));
    }

    private static String trimmed(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
