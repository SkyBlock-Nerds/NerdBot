package net.hypixel.nerdbot.app.command;

import net.aerh.imagegenerator.exception.GeneratorException;
import net.hypixel.nerdbot.app.command.GeneratorCommands.ItemModelFallback;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Choosing how an item is addressed: {@code item_id} and {@code item_model} are mutually
 * exclusive, and a render that fails because the pack cannot produce the addressed item model
 * degrades to the player head or the base item rather than failing the whole preview.
 */
class ItemModelAddressingTest {

    private static final String MODEL = "hypixel_skyblock:item/island_relevant/safari/safari_belt";
    private static final String SKIN = "0123456789abcdef";

    @Test
    void rejectsItemIdAndItemModelTogether() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.requireSingleItemAddress("paper", MODEL));

        assertEquals("The item_id and item_model options are mutually exclusive; use one or the other!",
            exception.getMessage());
    }

    @Test
    void acceptsEitherOptionAlone() {
        GeneratorCommands.requireSingleItemAddress("paper", null);
        GeneratorCommands.requireSingleItemAddress(null, MODEL);
        GeneratorCommands.requireSingleItemAddress(null, null);
    }

    @Test
    void treatsBlankOptionsAsAbsent() {
        GeneratorCommands.requireSingleItemAddress("  ", MODEL);
        GeneratorCommands.requireSingleItemAddress("paper", "  ");
    }

    @Test
    void requiresOneOfTheTwoOptionsWhereTheItemIsTheWholeRender() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> GeneratorCommands.requireAnItemAddress(null, "  "));

        assertEquals("Set either the item_id or the item_model option to pick what to render!", exception.getMessage());
        GeneratorCommands.requireAnItemAddress("paper", null);
        GeneratorCommands.requireAnItemAddress(null, MODEL);
    }

    @Test
    void doesNotRetryWhenTheItemWasNeverAddressedByModel() {
        assertEquals(ItemModelFallback.NONE, GeneratorCommands.itemModelFallback(null, SKIN),
            "a render failure unrelated to an item model must surface, not silently substitute");
        assertEquals(ItemModelFallback.NONE, GeneratorCommands.itemModelFallback("  ", SKIN));
    }

    @Test
    void retriesAsThePlayerHeadWhenTheItemHasAProfileTexture() {
        assertEquals(ItemModelFallback.PLAYER_HEAD, GeneratorCommands.itemModelFallback(MODEL, SKIN),
            "a profile texture is the closest thing to the model the pack could not produce");
    }

    @Test
    void retriesAsTheBaseItemWhenThereIsNoHeadTexture() {
        assertEquals(ItemModelFallback.ITEM_ID, GeneratorCommands.itemModelFallback(MODEL, null),
            "a pack that cannot produce the model must not stop the tooltip preview from rendering");
        assertEquals(ItemModelFallback.ITEM_ID, GeneratorCommands.itemModelFallback(MODEL, "  "));
    }

    @Test
    void explainsAnItemIdFallbackToTheUser() {
        assertEquals("The `hypixel:skyblock` pack could not resolve item model `" + MODEL
                + "`, so the base item `paper` was rendered instead. The pack is likely older than this item.",
            GeneratorCommands.itemModelFallbackNotice(ItemModelFallback.ITEM_ID, "hypixel:skyblock", MODEL, "minecraft:paper"));
    }

    @Test
    void explainsAPlayerHeadFallbackToTheUser() {
        assertEquals("The `hypixel:skyblock` pack could not resolve item model `" + MODEL
                + "`, so the item's player head texture was rendered instead. The pack is likely older than this item.",
            GeneratorCommands.itemModelFallbackNotice(ItemModelFallback.PLAYER_HEAD, "hypixel:skyblock", MODEL, "minecraft:player_head"));
    }

    @Test
    void namesVanillaWhenNoPackWasSelected() {
        assertEquals("Vanilla could not resolve item model `" + MODEL
                + "`, so the base item `paper` was rendered instead. The pack is likely older than this item.",
            GeneratorCommands.itemModelFallbackNotice(ItemModelFallback.ITEM_ID, null, MODEL, "paper"));
    }

    @Test
    void saysNothingWhenNothingWasSubstituted() {
        assertNull(GeneratorCommands.itemModelFallbackNotice(ItemModelFallback.NONE, "hypixel:skyblock", MODEL, "paper"));
    }
}
