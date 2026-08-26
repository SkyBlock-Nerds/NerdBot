package net.hypixel.nerdbot.app.command;

import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackRepository;
import net.hypixel.nerdbot.app.command.GeneratorCommands.ItemModelFallback;
import net.hypixel.nerdbot.app.command.GeneratorCommands.ParsedRender;
import net.hypixel.nerdbot.app.config.GeneratorConfig;
import net.hypixel.nerdbot.app.generation.pack.ResourcePackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code /gen parse} render path: an item model the pack cannot produce degrades to a
 * rendered stand-in instead of losing the whole preview, while failures that have nothing to do
 * with item models still surface to the user.
 */
class ParsedNbtFallbackRenderTest {

    private static final String RESOLVABLE = """
        {"id":"minecraft:paper","components":{"minecraft:item_model":"testpack:item/simple"}}""";
    private static final String UNRESOLVABLE = """
        {"id":"minecraft:paper","components":{"minecraft:item_model":"testpack:item/never_shipped"}}""";

    private ResourcePackService service;
    private PackId packId;

    @BeforeEach
    void registerFixturePack() throws IOException {
        // Fixtures live under target/ rather than @TempDir: pack sources are held open for the
        // JVM's lifetime, so on Windows a temp-dir cleanup would fail to delete them.
        Path root = Files.createDirectories(Path.of("target", "pack-fixtures"));
        Path packDir = Files.createTempDirectory(root, "fallback-");
        writeFixturePack(packDir);

        service = new ResourcePackService(new PackRepository());
        GeneratorConfig.PackDefinition definition = new GeneratorConfig.PackDefinition();
        definition.setId("nerdbot:fallback");
        definition.setPath(packDir.toString());
        GeneratorConfig.ResourcePackConfig config = new GeneratorConfig.ResourcePackConfig();
        config.setPacks(List.of(definition));
        service.registerConfiguredPacks(config);
        packId = PackId.parse("nerdbot:fallback");
    }

    @Test
    void rendersTheModelWhenThePackShipsIt() throws IOException {
        ParsedRender render = GeneratorCommands.renderParsedNbtWithFallback(service, RESOLVABLE, packId, null);

        assertEquals(ItemModelFallback.NONE, render.fallback(), "a model the pack ships needs no stand-in");
        assertNotNull(render.image());
    }

    @Test
    void rendersTheBaseItemWhenThePackCannotProduceTheModel() throws IOException {
        ParsedRender render = GeneratorCommands.renderParsedNbtWithFallback(service, UNRESOLVABLE, packId, null);

        assertEquals(ItemModelFallback.ITEM_ID, render.fallback());
        assertNotNull(render.image(), "the preview must still render rather than failing on a pack problem");
        assertEquals("testpack:item/never_shipped", render.parsedNbt().getParsedItemModel(),
            "the parse result still reports what the NBT asked for, so the notice can name it");
    }

    /**
     * Note this asserts the error reaches the user, not that the retry was skipped: with no item
     * model the stand-in would be the same item-id render that just failed, so both paths end in
     * the same exception. The guard against retrying is an efficiency and logging concern, not an
     * observable one.
     */
    @Test
    void surfacesFailuresThatHaveNothingToDoWithItemModels() {
        String unknownItem = """
            {"id":"minecraft:not_a_real_item_at_all","components":{}}""";

        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.renderParsedNbtWithFallback(service, unknownItem, packId, null));

        assertTrue(exception.getMessage().contains("Item with ID `not_a_real_item_at_all` not found"),
            "a bad item id must reach the user rather than being swallowed: " + exception.getMessage());
    }

    private static void writeFixturePack(Path root) throws IOException {
        write(root, "pack.mcmeta", "{\"pack\":{\"pack_format\":88,\"description\":\"fallback fixture\"}}");
        write(root, "assets/testpack/items/item/simple.json",
            "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"testpack:item/simple\"}}");
        write(root, "assets/testpack/models/item/simple.json",
            "{\"textures\":{\"layer0\":\"testpack:item/simple\"}}");

        BufferedImage texture = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = texture.createGraphics();
        graphics.setColor(Color.MAGENTA);
        graphics.fillRect(0, 0, 16, 16);
        graphics.dispose();

        Path png = root.resolve("assets/testpack/textures/item/simple.png");
        Files.createDirectories(png.getParent());
        ImageIO.write(texture, "png", png.toFile());
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path target = root.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}
