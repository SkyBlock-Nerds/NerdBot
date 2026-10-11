package net.hypixel.nerdbot.tooling.config;

import net.hypixel.nerdbot.app.config.NerdBotConfig;
import net.hypixel.nerdbot.discord.config.ExampleValue;
import net.hypixel.nerdbot.discord.config.NoExampleValue;
import net.hypixel.nerdbot.marmalade.json.DataSerialization;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Writes example-config.json from the config classes. Every field keeps the default its class
 * gives it; only empty values (null, or an empty collection or array) get an example, so the file
 * shows what the bot actually runs with. Fields can override that with {@link ExampleValue} or
 * opt out with {@link NoExampleValue}. Anything it can't build is an error rather than a gap in
 * the output.
 */
public class ConfigGenerator {

    static final String EXAMPLE_DISCORD_ID = "1234567890123456789";
    static final String EXAMPLE_STRING = "example_value";
    static final String EXAMPLE_URL = "https://example.com/";
    private static final int MAX_DEPTH = 10;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: ConfigGenerator <output-path>");
            System.exit(1);
        }

        try {
            String json = DataSerialization.PRETTY_GSON.toJson(new ConfigGenerator().generate(NerdBotConfig.class));
            Files.writeString(Path.of(args[0]), json, StandardCharsets.UTF_8);
            System.out.println("Created JSON file: " + args[0]);
        } catch (IOException | RuntimeException e) {
            System.err.println("Failed to generate the example config:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    public <T> T generate(Class<T> configClass) {
        T instance = newInstance(configClass, configClass.getSimpleName());
        fillEmptyFields(instance, configClass.getSimpleName(), 0);
        return instance;
    }

    private void fillEmptyFields(Object instance, String path, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException("Config nesting goes deeper than " + MAX_DEPTH + " levels at " + path);
        }

        for (Field field : configFields(instance.getClass())) {
            String fieldPath = path + "." + field.getName();
            try {
                field.setAccessible(true);
                Object current = field.get(instance);
                Object example = exampleFor(field, current, fieldPath, depth);
                if (example != current) {
                    field.set(instance, example);
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Could not access " + fieldPath, e);
            }
        }
    }

    private Object exampleFor(Field field, Object current, String path, int depth) {
        if (field.isAnnotationPresent(NoExampleValue.class)) {
            return current;
        }
        if (field.isAnnotationPresent(ExampleValue.class)) {
            return fromAnnotation(field, field.getAnnotation(ExampleValue.class).value(), path);
        }
        if (current == null) {
            return newValue(field.getType(), field.getGenericType(), field.getName(), path, depth);
        }
        if (current instanceof String && isEmpty(current)) {
            return exampleString(field.getName());
        }
        if (isEmpty(current)) {
            return withOneElement(current, field.getGenericType(), field.getName(), path, depth);
        }
        if (isConfigObject(current.getClass())) {
            fillEmptyFields(current, path, depth + 1);
        }
        return current;
    }

    private static Object fromAnnotation(Field field, String raw, String path) {
        Class<?> type = field.getType();
        if (type.isArray()) {
            Object array = Array.newInstance(type.getComponentType(), 1);
            Array.set(array, 0, scalar(type.getComponentType(), raw));
            return array;
        }
        if (Collection.class.isAssignableFrom(type)) {
            return Set.class.isAssignableFrom(type) ? new HashSet<>(Set.of(raw)) : new ArrayList<>(List.of(raw));
        }

        Object value = scalar(type, raw);
        if (value == null) {
            throw new IllegalStateException("@ExampleValue is not supported on " + path + " (" + type.getSimpleName() + ")");
        }
        return value;
    }

    private static Object scalar(Class<?> type, String raw) {
        if (type == String.class) {
            return raw;
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(raw);
        }
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(raw);
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(raw);
        }
        if (type == double.class || type == Double.class) {
            return Double.parseDouble(raw);
        }
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(raw)) {
                    return constant;
                }
            }
            throw new IllegalArgumentException(raw + " is not a constant of " + type.getSimpleName());
        }
        return null;
    }

    private Object newValue(Class<?> type, Type genericType, String name, String path, int depth) {
        if (type == String.class) {
            return exampleString(name);
        }
        if (type == boolean.class || type == Boolean.class) {
            return false;
        }
        if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
            Object number = scalar(type, "1");
            if (number == null) {
                throw new IllegalStateException("No example for " + type.getSimpleName() + " at " + path);
            }
            return number;
        }
        if (type.isEnum()) {
            return type.getEnumConstants()[0];
        }
        if (type.isArray()) {
            Object array = Array.newInstance(type.getComponentType(), 1);
            Array.set(array, 0, newValue(type.getComponentType(), type.getComponentType(), name, path + "[]", depth + 1));
            return array;
        }
        if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
            Object empty = Set.class.isAssignableFrom(type) ? new HashSet<>()
                : Map.class.isAssignableFrom(type) ? new HashMap<>()
                : new ArrayList<>();
            return withOneElement(empty, genericType, name, path, depth);
        }

        Object nested = hasNoArgConstructor(type) ? newInstance(type, path) : viaWidestConstructor(type, path, depth);
        fillEmptyFields(nested, path, depth + 1);
        return nested;
    }

    /** For classes like Marmalade's Badge that only take their values through a constructor. */
    private Object viaWidestConstructor(Class<?> type, String path, int depth) {
        Constructor<?> widest = Arrays.stream(type.getDeclaredConstructors())
            .max(Comparator.comparingInt(Constructor::getParameterCount))
            .orElseThrow(() -> new IllegalStateException("Config class " + type.getName() + " at " + path + " has no constructor"));

        Parameter[] parameters = widest.getParameters();
        Object[] arguments = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            arguments[i] = newValue(parameter.getType(), parameter.getParameterizedType(), parameter.getName(),
                path + "(" + parameter.getName() + ")", depth + 1);
        }

        try {
            widest.setAccessible(true);
            return widest.newInstance(arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not construct " + type.getName() + " at " + path, e);
        }
    }

    private static boolean hasNoArgConstructor(Class<?> type) {
        return Arrays.stream(type.getDeclaredConstructors()).anyMatch(constructor -> constructor.getParameterCount() == 0);
    }

    @SuppressWarnings("unchecked")
    private Object withOneElement(Object empty, Type genericType, String name, String path, int depth) {
        if (empty.getClass().isArray()) {
            return newValue(empty.getClass(), empty.getClass(), name, path, depth);
        }

        Type[] typeArguments = genericType instanceof ParameterizedType parameterized ? parameterized.getActualTypeArguments() : new Type[0];
        // Defaults are often immutable (List.of()), so the example goes into a fresh collection of the same kind
        Object filled = mutableLike(empty);
        if (filled instanceof Map<?, ?> map) {
            if (typeArguments.length < 2) {
                throw new IllegalStateException("Can't tell the key and value types of " + path);
            }
            Object key = newValue(rawClass(typeArguments[0], path), typeArguments[0], "key", path + ".key", depth + 1);
            Object value = newValue(rawClass(typeArguments[1], path), typeArguments[1], name, path + ".value", depth + 1);
            ((Map<Object, Object>) map).put(key, value);
            return map;
        }

        if (typeArguments.length < 1) {
            throw new IllegalStateException("Can't tell the element type of " + path);
        }
        Object element = newValue(rawClass(typeArguments[0], path), typeArguments[0], name, path + "[]", depth + 1);
        ((Collection<Object>) filled).add(element);
        return filled;
    }

    private static Object mutableLike(Object collection) {
        try {
            return collection.getClass().getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            if (collection instanceof Map<?, ?>) {
                return new LinkedHashMap<>();
            }
            return collection instanceof Set<?> ? new LinkedHashSet<>() : new ArrayList<>();
        }
    }

    /** Ids and lists of ids are Discord snowflakes unless an {@link ExampleValue} says otherwise. */
    static String exampleString(String fieldName) {
        String name = fieldName.toLowerCase(Locale.ROOT);
        if (name.endsWith("id") || name.endsWith("ids")) {
            return EXAMPLE_DISCORD_ID;
        }
        if (name.contains("url")) {
            return EXAMPLE_URL;
        }
        return EXAMPLE_STRING;
    }

    private static boolean isEmpty(Object value) {
        return value instanceof String string && string.isBlank()
            || value instanceof Collection<?> collection && collection.isEmpty()
            || value instanceof Map<?, ?> map && map.isEmpty()
            || value.getClass().isArray() && Array.getLength(value) == 0;
    }

    private static boolean isConfigObject(Class<?> type) {
        return !type.isPrimitive() && !type.isEnum() && !type.isArray()
            && !type.getName().startsWith("java.")
            && !Collection.class.isAssignableFrom(type) && !Map.class.isAssignableFrom(type);
    }

    private static Class<?> rawClass(Type type, String path) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        throw new IllegalStateException("Unsupported generic type " + type + " at " + path);
    }

    private static <T> T newInstance(Class<T> type, String path) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Config class " + type.getName() + " at " + path + " needs a no-argument constructor", e);
        }
    }

    private static List<Field> configFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }
}
