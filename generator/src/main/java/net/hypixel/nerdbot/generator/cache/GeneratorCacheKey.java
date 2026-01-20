package net.hypixel.nerdbot.generator.cache;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Utility methods for creating stable cache keys for generator configurations.
 */
public final class GeneratorCacheKey {

    private static final String NULL_VALUE = "null";
    private static final HexFormat HEX = HexFormat.of();

    private GeneratorCacheKey() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Creates a deterministic cache key for the supplied generator instance by hashing all of its final fields.
     *
     * @param generator Generator instance (typically {@code this} inside {@code generate()}).
     *
     * @return Stable cache key derived from the generator configuration.
     */
    public static String fromGenerator(Object generator) {
        if (generator == null) {
            throw new IllegalArgumentException("Generator cannot be null");
        }

        String canonical = buildCanonicalRepresentation(generator);
        return generator.getClass().getName() + "|" + hash(canonical);
    }

    private static String buildCanonicalRepresentation(Object target) {
        StringBuilder builder = new StringBuilder();
        List<Field> fields = collectFields(target.getClass());

        for (Field field : fields) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || !Modifier.isFinal(modifiers)) {
                continue;
            }

            field.setAccessible(true);
            Object value;
            try {
                value = field.get(target);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Unable to access field '" + field.getName() + "' for cache key generation", exception);
            }

            builder.append(field.getName())
                .append('=')
                .append(normalizeValue(value))
                .append(';');
        }

        return builder.toString();
    }

    private static List<Field> collectFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            Field[] declaredFields = current.getDeclaredFields();
            fields.addAll(Arrays.asList(declaredFields));
            current = current.getSuperclass();
        }

        fields.sort(Comparator.comparing(Field::getName));
        return fields;
    }

    private static String normalizeValue(Object value) {
        switch (value) {
            case null -> {
                return NULL_VALUE;
            }
            case Enum<?> enumValue -> {
                return enumValue.name();
            }
            case CharSequence sequence -> {
                return abbreviate(sequence.toString());
            }
            default -> {
            }
        }

        if (value.getClass().isArray()) {
            return abbreviate(arrayToString(value));
        }

        if (value instanceof Iterable<?> iterable) {
            List<String> parts = new ArrayList<>();
            for (Object element : iterable) {
                parts.add(normalizeValue(element));
            }
            return abbreviate(parts.toString());
        }

        return abbreviate(Objects.toString(value));
    }

    private static String arrayToString(Object array) {
        int length = Array.getLength(array);
        List<String> parts = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            Object element = Array.get(array, index);
            parts.add(normalizeValue(element));
        }
        return parts.toString();
    }

    private static String abbreviate(String value) {
        if (value.length() <= 64) {
            return value;
        }
        return hash(value);
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }
}
