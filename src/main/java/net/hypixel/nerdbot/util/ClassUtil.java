package net.hypixel.nerdbot.util;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClassUtil {

    private static final Map<String, List<Class<?>>> packageClassCache = new ConcurrentHashMap<>();

    private ClassUtil() {
    }

    public static List<Class<?>> getClassesInPackage(String packageName) throws IOException, ClassNotFoundException, URISyntaxException {
        List<Class<?>> cachedClasses = packageClassCache.get(packageName);
        if (cachedClasses != null) {
            return cachedClasses;
        }

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String path = packageName.replace('.', File.separatorChar);
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<>();
        List<Class<?>> classes = new ArrayList<>();

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.toURI()));
        }

        for (File directory : dirs) {
            classes.addAll(findClasses(directory, packageName));
        }

        // Cache the result for this package
        packageClassCache.put(packageName, classes);

        return classes;
    }

    private static List<Class<?>> findClasses(File directory, String packageName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        if (!directory.exists()) {
            return classes;
        }
        File[] files = directory.listFiles();

        for (File file : files) {
            if (file.isDirectory()) {
                if (!file.getName().contains(".")) {
                    classes.addAll(findClasses(file, packageName + "." + file.getName()));
                }
            } else if (file.getName().endsWith(".class")) {
                classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
            }
        }
        return classes;
    }
}
