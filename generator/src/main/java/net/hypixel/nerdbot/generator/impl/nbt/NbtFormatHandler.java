package net.hypixel.nerdbot.generator.impl.nbt;

import com.google.gson.JsonObject;

public interface NbtFormatHandler {

    /**
     * Determines if this handler can parse the provided NBT structure.
     *
     * @param nbt root NBT object
     *
     * @return true if this handler supports the structure
     */
    boolean supports(JsonObject nbt);

    /**
     * Extracts metadata specific to this NBT format (player head texture, max line length, etc).
     *
     * @param nbt root NBT object
     *
     * @return metadata describing this format's fields, or {@link NbtFormatMetadata#EMPTY} when none apply
     */
    default NbtFormatMetadata extractMetadata(JsonObject nbt) {
        return NbtFormatMetadata.EMPTY;
    }
}