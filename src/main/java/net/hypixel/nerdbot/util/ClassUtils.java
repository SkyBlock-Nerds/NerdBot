package net.hypixel.nerdbot.util;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import java.util.HashSet;
import java.util.Set;

public class ClassUtils {

    private ClassUtils() {
    }

    public static Set<Class<?>> findClasses(String packageName, Class<?> clazz) {
        Reflections reflections = new Reflections(packageName, new SubTypesScanner(false));
        return new HashSet<>(reflections.getSubTypesOf(clazz));
    }
}
