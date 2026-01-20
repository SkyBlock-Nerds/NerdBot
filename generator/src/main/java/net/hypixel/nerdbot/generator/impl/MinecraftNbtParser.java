package net.hypixel.nerdbot.generator.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.generator.Generator;
import net.hypixel.nerdbot.generator.builder.ClassBuilder;
import net.hypixel.nerdbot.generator.exception.NbtParseException;
import net.hypixel.nerdbot.generator.impl.nbt.ComponentsNbtFormatHandler;
import net.hypixel.nerdbot.generator.impl.nbt.LegacyNbtFormatHandler;
import net.hypixel.nerdbot.generator.impl.nbt.NbtFormatHandler;
import net.hypixel.nerdbot.generator.impl.nbt.NbtFormatMetadata;
import net.hypixel.nerdbot.generator.impl.tooltip.MinecraftTooltipGenerator;
import net.hypixel.nerdbot.generator.parser.text.PlaceholderReverseMapper;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MinecraftNbtParser {

    private static final NbtFormatHandler DEFAULT_FORMAT_HANDLER = new DefaultNbtFormatHandler();

    private static final List<NbtFormatHandler> FORMAT_HANDLERS = List.of(
        new ComponentsNbtFormatHandler(),
        new LegacyNbtFormatHandler()
    );

    public static ParsedNbt parse(String nbt) {
        JsonObject jsonObject = JsonParser.parseString(nbt).getAsJsonObject();
        ArrayList<ClassBuilder<? extends Generator>> generators = new ArrayList<>();

        if (!jsonObject.has("id")) {
            throw new NbtParseException("NBT data is missing required 'id' field");
        }

        if (jsonObject.get("id").getAsString().contains("skull")) {
            String value = jsonObject.get("id").getAsString();
            value = value.replace("minecraft:", "")
                .replace("skull", "player_head");
            jsonObject.addProperty("id", value);
            log.debug("Normalized skull item id to '{}'", value);
        }

        // Handle player head for both legacy and component formats
        String parsedItemId = jsonObject.get("id").getAsString();
        boolean isPlayerHead = isPlayerHeadId(parsedItemId);
        NbtFormatHandler formatHandler = resolveFormatHandler(jsonObject);
        log.debug("Parsing item '{}' with NBT format handler '{}'", parsedItemId, handlerName(formatHandler));
        NbtFormatMetadata formatMetadata = formatHandler.extractMetadata(jsonObject);
        boolean hasTextureMetadata = formatMetadata.containsKey(NbtFormatMetadata.KEY_PLAYER_HEAD_TEXTURE);
        Integer metadataMaxLineLength = formatMetadata.get(NbtFormatMetadata.KEY_MAX_LINE_LENGTH, Integer.class);
        Boolean metadataEnchanted = formatMetadata.get(NbtFormatMetadata.KEY_ENCHANTED, Boolean.class);
        boolean enchanted = metadataEnchanted != null && metadataEnchanted;

        log.debug(
            "Extracted metadata via '{}': texturePresent={}, maxLineLength={}",
            handlerName(formatHandler),
            hasTextureMetadata,
            metadataMaxLineLength
        );

        String base64Texture = null;
        if (isPlayerHead) {
            base64Texture = formatMetadata.get(NbtFormatMetadata.KEY_PLAYER_HEAD_TEXTURE, String.class);
            if (base64Texture != null) {
                log.debug("Resolved player head texture via '{}' (length={})", handlerName(formatHandler), base64Texture.length());
            } else {
                log.debug("Handler '{}' did not provide a player head texture; falling back to static item render", handlerName(formatHandler));
            }

            if (base64Texture != null) {
                generators.add(new MinecraftPlayerHeadGenerator.Builder()
                    .withSkin(base64Texture)
                    .withScale(-2));
            } else {
                generators.add(new MinecraftItemGenerator.Builder()
                    .withItem(parsedItemId)
                    .isEnchanted(enchanted)
                    .isBigImage());
            }
        } else {
            generators.add(new MinecraftItemGenerator.Builder()
                .withItem(parsedItemId)
                .isEnchanted(enchanted)
                .isBigImage());
        }

        int maxLineLength = resolveMaxLineLength(formatMetadata, formatHandler);
        log.debug("Using max line length {} for item '{}' (handler='{}')", maxLineLength, parsedItemId, handlerName(formatHandler));

        MinecraftTooltipGenerator.Builder tooltipGenerator = new MinecraftTooltipGenerator.Builder()
            .parseNbtJson(jsonObject)
            .withRenderBorder(true)
            .hasFirstLinePadding(true)
            .withMaxLineLength(maxLineLength);

        // Extract dye color and apply to item generator if it exists
        String dyeColor = tooltipGenerator.getDyeColor(jsonObject);
        if (dyeColor != null && !isPlayerHead) {
            log.debug("Detected dye color '{}' for item '{}'", dyeColor, parsedItemId);
            // Update the item generator with dye color
            generators = new ArrayList<>();
            generators.add(new MinecraftItemGenerator.Builder()
                .withItem(jsonObject.get("id").getAsString())
                .withData(dyeColor)
                .isEnchanted(enchanted)
                .isBigImage());
        } else if (dyeColor == null) {
            log.trace("No dye color present for item '{}'", parsedItemId);
        } else {
            log.trace("Ignoring dye color '{}' because '{}' renders as a player head", dyeColor, parsedItemId);
        }

        generators.add(tooltipGenerator);

        PlaceholderReverseMapper reverseMapper = new PlaceholderReverseMapper();
        String mappedLore = reverseMapper.mapPlaceholders(tooltipGenerator.getItemLore());
        String mappedName = reverseMapper.mapPlaceholders(tooltipGenerator.getItemName());

        tooltipGenerator
            .withItemLore(mappedLore)
            .withName(mappedName);

        return new ParsedNbt(generators, base64Texture, parsedItemId, enchanted);
    }

    private static boolean isPlayerHeadId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }

        String normalizedId = itemId.toLowerCase();
        if (normalizedId.startsWith("minecraft:")) {
            normalizedId = normalizedId.substring("minecraft:".length());
        }

        return normalizedId.equals("player_head");
    }

    private static NbtFormatHandler resolveFormatHandler(JsonObject jsonObject) {
        for (NbtFormatHandler handler : FORMAT_HANDLERS) {
            if (handler.supports(jsonObject)) {
                return handler;
            }
        }

        log.info("No specific NBT handler matched payload, using default handler");
        return DEFAULT_FORMAT_HANDLER;
    }

    private static int resolveMaxLineLength(NbtFormatMetadata metadata, NbtFormatHandler handler) {
        Integer maxLineLength = metadata.get(NbtFormatMetadata.KEY_MAX_LINE_LENGTH, Integer.class);
        if (maxLineLength == null) {
            log.debug("Handler '{}' did not specify max line length; defaulting to {}", handlerName(handler), MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH);
            return MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH;
        }

        return maxLineLength;
    }

    private static String handlerName(NbtFormatHandler handler) {
        if (handler == null) {
            return "null";
        }
        String simpleName = handler.getClass().getSimpleName();
        return simpleName.isBlank() ? handler.getClass().getName() : simpleName;
    }

    @Getter(AccessLevel.PUBLIC)
    @AllArgsConstructor
    public static class ParsedNbt {

        private ArrayList<ClassBuilder<? extends Generator>> generators;
        private String base64Texture;
        private String parsedItemId;
        private boolean enchanted;
    }

    private static final class DefaultNbtFormatHandler implements NbtFormatHandler {

        @Override
        public boolean supports(JsonObject nbt) {
            return true;
        }
    }
}