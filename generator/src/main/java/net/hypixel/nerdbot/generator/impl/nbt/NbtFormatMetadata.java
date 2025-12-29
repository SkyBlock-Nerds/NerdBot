package net.hypixel.nerdbot.generator.impl.nbt;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class NbtFormatMetadata {

    public static final String KEY_PLAYER_HEAD_TEXTURE = "player_head_texture";
    public static final String KEY_MAX_LINE_LENGTH = "max_line_length";

    public static final NbtFormatMetadata EMPTY = new NbtFormatMetadata(Collections.emptyMap());

    private final Map<String, Object> data;

    private NbtFormatMetadata(Map<String, Object> data) {
        this.data = data == null || data.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(data);
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    public Map<String, Object> asMap() {
        return data;
    }

    public <T> T get(String key, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = data.get(key);
        if (value == null) {
            return null;
        }

        if (!type.isInstance(value)) {
            throw new ClassCastException("Metadata value for key '" + key + "' is not of type " + type.getName());
        }

        return type.cast(value);
    }

    public static final class Builder {
        private final Map<String, Object> data = new HashMap<>();

        public Builder withValue(String key, Object value) {
            if (key == null || key.isBlank() || value == null) {
                return this;
            }
            data.put(key, value);
            return this;
        }

        public Builder merge(Map<String, Object> other) {
            if (other == null || other.isEmpty()) {
                return this;
            }

            other.forEach(this::withValue);
            return this;
        }

        public NbtFormatMetadata build() {
            if (data.isEmpty()) {
                return EMPTY;
            }

            return new NbtFormatMetadata(new HashMap<>(data));
        }
    }
}