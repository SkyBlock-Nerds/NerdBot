package net.hypixel.skyblocknerds.utilities;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import java.util.HashSet;
import java.util.Set;

public class ClassUtilities {

    /**
     * Find all classes in a package that extend a specific class.
     *
     * @param packageName package to search in
     * @param clazz       class to search for
     * @return set of classes that extend the specified class
     */
    public static Set<Class<?>> findClasses(String packageName, Class<?> clazz) {
        Reflections reflections = new Reflections(packageName, new SubTypesScanner(false));
        return new HashSet<>(reflections.getSubTypesOf(clazz));
    }
}
