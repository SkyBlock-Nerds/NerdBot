package net.hypixel.skyblocknerds.utilities;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class JsonUtils {

    public static <T> Map<String, Object> convertToMap(T obj) throws IllegalAccessException {
        Map<String, Object> map = new HashMap<>();
        for (Field field : obj.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            map.put(field.getName(), field.get(obj));
        }
        return map;
    }
}
