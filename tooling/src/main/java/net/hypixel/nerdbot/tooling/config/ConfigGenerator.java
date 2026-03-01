package net.hypixel.nerdbot.tooling.config;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.hypixel.nerdbot.discord.config.NerdBotConfig;
import net.hypixel.nerdbot.marmalade.json.DataSerialization;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"UnusedReturnValue", "ResultOfMethodCallIgnored"})
public class ConfigGenerator {

    private static final String EXAMPLE_DISCORD_ID = "1234567890123456789";
    private static final String EXAMPLE_STRING = "example_value";
    private static final String EXAMPLE_URL = "https://example.com/";

    // Prevent infinite recursion
    private final Set<Class<?>> visitedClasses = new HashSet<>();
    private final int maxDepth;

    public ConfigGenerator() {
        this(10); // Default max depth
    }

    public ConfigGenerator(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: ConfigGenerator <output-path>");
            System.exit(-1);
        }

        ConfigGenerator generator = new ConfigGenerator();

        try {
            NerdBotConfig config = generator.generate(NerdBotConfig.class);
            String json = DataSerialization.PRETTY_GSON.toJson(config);
            writeJsonToFile(json, args[0]);
            System.out.println("Created JSON file: " + args[0]);
        } catch (Exception e) {
            System.err.println("Failed to generate config: " + e.getMessage());
            System.exit(-1);
        }
    }

    private static void writeJsonToFile(String json, String outputPath) throws IOException {
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(json);
        }
    }

    /**
     * Generate an example instance of the given config class.
     *
     * @param clazz the config class to generate
     * @param <T>   the type of the config class
     *
     * @return a populated instance with example values
     */
    public <T> T generate(Class<T> clazz) {
        visitedClasses.clear();
        return generateInstance(clazz, 0);
    }

    private <T> T generateInstance(Class<T> clazz, int depth) {
        if (depth > maxDepth) {
            return null;
        }

        // Prevent infinite recursion
        if (visitedClasses.contains(clazz) && depth > 2) {
            return null;
        }
        visitedClasses.add(clazz);

        try {
            T instance = createInstance(clazz, depth);
            if (instance == null) {
                return null;
            }

            for (Field field : getAllFields(clazz)) {
                if (Modifier.isStatic(field.getModifiers()) ||
                    Modifier.isTransient(field.getModifiers())) {
                    continue;
                }

                field.setAccessible(true);

                if (Modifier.isFinal(field.getModifiers())) {
                    populateFinalCollectionField(field, instance, depth);
                    continue;
                }

                Object value = generateValueForField(field, depth);

                if (value != null) {
                    field.set(instance, value);
                }
            }

            return instance;
        } catch (Exception e) {
            System.err.println("Failed to generate instance of " + clazz.getName() + ": " + e.getMessage());
            return null;
        } finally {
            visitedClasses.remove(clazz);
        }
    }

    @SuppressWarnings("unchecked")
    private void populateFinalCollectionField(Field field, Object instance, int depth) {
        try {
            Object existingValue = field.get(instance);
            if (existingValue == null) {
                return;
            }

            Type genericType = field.getGenericType();
            String fieldName = field.getName().toLowerCase();

            switch (existingValue) {
                case Map<?, ?> map when map.isEmpty() -> {
                    if (genericType instanceof ParameterizedType paramType) {
                        Type[] typeArgs = paramType.getActualTypeArguments();

                        if (typeArgs.length >= 2) {
                            Class<?> keyClass = getClassFromType(typeArgs[0]);
                            Class<?> valueClass = getClassFromType(typeArgs[1]);

                            if (keyClass != null && valueClass != null) {
                                Object key = generateValue(keyClass, typeArgs[0], "key", false, depth + 1);
                                Object value = generateValue(valueClass, typeArgs[1], fieldName, false, depth + 1);

                                if (key != null && value != null) {
                                    ((Map<Object, Object>) existingValue).put(key, value);
                                }
                            }
                        }
                    }
                }
                case List<?> list when list.isEmpty() -> {
                    if (genericType instanceof ParameterizedType paramType) {
                        Type[] typeArgs = paramType.getActualTypeArguments();

                        if (typeArgs.length > 0) {
                            Class<?> elementClass = getClassFromType(typeArgs[0]);

                            if (elementClass != null) {
                                boolean isDiscordId = fieldName.contains("id");
                                Object element = generateValue(elementClass, typeArgs[0], fieldName, isDiscordId, depth + 1);

                                if (element != null) {
                                    ((List<Object>) existingValue).add(element);
                                }
                            }
                        }
                    }
                }
                case Set<?> set when set.isEmpty() -> {
                    if (genericType instanceof ParameterizedType paramType) {
                        Type[] typeArgs = paramType.getActualTypeArguments();

                        if (typeArgs.length > 0) {
                            Class<?> elementClass = getClassFromType(typeArgs[0]);

                            if (elementClass != null) {
                                boolean isDiscordId = fieldName.contains("id");
                                Object element = generateValue(elementClass, typeArgs[0], fieldName, isDiscordId, depth + 1);

                                if (element != null) {
                                    ((Set<Object>) existingValue).add(element);
                                }
                            }
                        }
                    }
                }
                default -> {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private Object generateValueForField(Field field, int depth) {
        Class<?> type = field.getType();
        Type genericType = field.getGenericType();
        String fieldName = field.getName().toLowerCase();

        boolean isDiscordId = fieldName.endsWith("id") || fieldName.endsWith("ids");

        return generateValue(type, genericType, fieldName, isDiscordId, depth);
    }

    private Object generateValue(Class<?> type, Type genericType, String fieldName, boolean isDiscordId, int depth) {
        if (type == String.class) {
            return generateStringValue(fieldName, isDiscordId);
        }
        
        if (type == int.class || type == Integer.class) {
            return 1;
        }

        if (type == long.class || type == Long.class) {
            return 1L;
        }

        if (type == boolean.class || type == Boolean.class) {
            return true;
        }

        if (type == double.class || type == Double.class) {
            return 1.0;
        }

        if (type == float.class || type == Float.class) {
            return 1.0f;
        }

        if (type == short.class || type == Short.class) {
            return (short) 1;
        }

        if (type == byte.class || type == Byte.class) {
            return (byte) 1;
        }

        if (type == char.class || type == Character.class) {
            return 'A';
        }

        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants.length > 0 ? constants[0] : null;
        }

        if (type == TimeUnit.class) {
            return TimeUnit.MINUTES;
        }

        if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            Object array = Array.newInstance(componentType, 1);
            Object elementValue = generateValue(componentType, componentType, fieldName, isDiscordId, depth + 1);

            if (elementValue != null) {
                Array.set(array, 0, elementValue);
            }

            return array;
        }

        if (List.class.isAssignableFrom(type)) {
            return generateList(genericType, fieldName, depth);
        }

        if (Set.class.isAssignableFrom(type)) {
            return generateSet(genericType, fieldName, depth);
        }

        if (Map.class.isAssignableFrom(type)) {
            return generateMap(genericType, fieldName, depth);
        }

        // Nested objects
        return generateInstance(type, depth + 1);
    }

    private String generateStringValue(String fieldName, boolean isDiscordId) {
        if (isDiscordId) {
            return EXAMPLE_DISCORD_ID;
        }
        if (fieldName.contains("url")) {
            return EXAMPLE_URL;
        }
        return EXAMPLE_STRING;
    }

    private List<?> generateList(Type genericType, String fieldName, int depth) {
        List<Object> list = new ArrayList<>();

        if (genericType instanceof ParameterizedType paramType) {
            Type[] typeArgs = paramType.getActualTypeArguments();

            if (typeArgs.length > 0) {
                Type elementType = typeArgs[0];
                Class<?> elementClass = getClassFromType(elementType);

                if (elementClass != null) {
                    boolean isDiscordId = fieldName.toLowerCase().contains("id");
                    Object element = generateValue(elementClass, elementType, fieldName, isDiscordId, depth + 1);

                    if (element != null) {
                        list.add(element);
                    }
                }
            }
        }

        return list;
    }

    private Set<?> generateSet(Type genericType, String fieldName, int depth) {
        Set<Object> set = new HashSet<>();

        if (genericType instanceof ParameterizedType paramType) {
            Type[] typeArgs = paramType.getActualTypeArguments();

            if (typeArgs.length > 0) {
                Type elementType = typeArgs[0];
                Class<?> elementClass = getClassFromType(elementType);

                if (elementClass != null) {
                    boolean isDiscordId = fieldName.toLowerCase().contains("id");
                    Object element = generateValue(elementClass, elementType, fieldName, isDiscordId, depth + 1);

                    if (element != null) {
                        set.add(element);
                    }
                }
            }
        }

        return set;
    }

    private Map<?, ?> generateMap(Type genericType, String fieldName, int depth) {
        Map<Object, Object> map = new HashMap<>();

        if (genericType instanceof ParameterizedType paramType) {
            Type[] typeArgs = paramType.getActualTypeArguments();
            if (typeArgs.length >= 2) {
                Class<?> keyClass = getClassFromType(typeArgs[0]);
                Class<?> valueClass = getClassFromType(typeArgs[1]);

                if (keyClass != null && valueClass != null) {
                    Object key = generateValue(keyClass, typeArgs[0], "key", false, depth + 1);
                    Object value = generateValue(valueClass, typeArgs[1], fieldName, false, depth + 1);

                    if (key != null && value != null) {
                        map.put(key, value);
                    }
                }
            }
        }

        return map;
    }

    private Class<?> getClassFromType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }

        if (type instanceof ParameterizedType paramType) {
            Type rawType = paramType.getRawType();
            if (rawType instanceof Class<?> clazz) {
                return clazz;
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T createInstance(Class<T> clazz, int depth) {
        try {
            // Try no-arg constructor first
            Constructor<T> noArgConstructor = clazz.getDeclaredConstructor();
            noArgConstructor.setAccessible(true);
            return noArgConstructor.newInstance();
        } catch (NoSuchMethodException e) {
            // Try to find any constructor and use example values based on parameter names
            Constructor<?>[] constructors = clazz.getDeclaredConstructors();
            for (Constructor<?> constructor : constructors) {
                try {
                    constructor.setAccessible(true);
                    Parameter[] parameters = constructor.getParameters();
                    Object[] params = new Object[parameters.length];

                    for (int i = 0; i < parameters.length; i++) {
                        Parameter param = parameters[i];
                        String paramName = param.getName().toLowerCase();
                        Class<?> paramType = param.getType();
                        Type genericType = param.getParameterizedType();

                        boolean isDiscordId = paramName.endsWith("id") || paramName.endsWith("ids");

                        params[i] = generateValue(paramType, genericType, paramName, isDiscordId, depth + 1);
                    }

                    return (T) constructor.newInstance(params);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to create instance of " + clazz.getName() + ": " + e.getMessage());
        }

        return null;
    }

    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;

        while (current != null && current != Object.class) {
            Collections.addAll(fields, current.getDeclaredFields());
            current = current.getSuperclass();
        }

        return fields;
    }
}
