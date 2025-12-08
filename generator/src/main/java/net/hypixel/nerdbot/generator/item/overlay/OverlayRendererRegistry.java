package net.hypixel.nerdbot.generator.item.overlay;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.exception.GeneratorException;
import net.hypixel.nerdbot.generator.item.overlay.renderer.DualLayerOverlayRenderer;
import net.hypixel.nerdbot.generator.item.overlay.renderer.MappedOverlayRenderer;
import net.hypixel.nerdbot.generator.item.overlay.renderer.NormalOverlayRenderer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for OverlayRenderer implementations.
 */
@Slf4j
public class OverlayRendererRegistry {

    private static final Map<String, OverlayRenderer> RENDERERS = new ConcurrentHashMap<>();

    static {
        registerRenderer(new NormalOverlayRenderer());
        registerRenderer(new MappedOverlayRenderer());
        registerRenderer(new DualLayerOverlayRenderer());
    }

    private OverlayRendererRegistry() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Register an overlay renderer.
     * The renderer will be accessible by its type name (case-insensitive).
     *
     * @param renderer The renderer to register
     */
    public static void registerRenderer(OverlayRenderer renderer) {
        RENDERERS.put(renderer.getTypeName().toUpperCase(), renderer);
        log.info("Registered overlay renderer: {}", renderer.getTypeName());
    }

    /**
     * Get a renderer by type name.
     *
     * @param typeName The renderer type name (case-insensitive)
     *
     * @return Optional containing the renderer if found, empty otherwise
     */
    public static Optional<OverlayRenderer> getRenderer(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(RENDERERS.get(typeName.toUpperCase()));
    }

    /**
     * Get a renderer by type name, throwing an exception if not found.
     *
     * @param typeName The renderer type name (case-insensitive)
     *
     * @return The renderer
     *
     * @throws GeneratorException if the renderer is not found
     */
    public static OverlayRenderer getRendererOrThrow(String typeName) {
        return getRenderer(typeName).orElseThrow(() -> new GeneratorException("Unknown overlay type: " + typeName));
    }
}