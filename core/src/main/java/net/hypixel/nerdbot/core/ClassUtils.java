package net.hypixel.nerdbot.core;

import lombok.experimental.UtilityClass;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import java.util.HashSet;
import java.util.Set;

@UtilityClass
public class ClassUtils {

    public static Set<Class<?>> findClasses(String packageName, Class<?> clazz) {
        Reflections reflections = new Reflections(packageName, new SubTypesScanner(false));
        return new HashSet<>(reflections.getSubTypesOf(clazz));
    }
}