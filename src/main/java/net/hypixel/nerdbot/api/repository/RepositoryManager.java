package net.hypixel.nerdbot.api.repository;

import com.mongodb.client.MongoClient;
import lombok.SneakyThrows;
import net.hypixel.nerdbot.util.ClassUtil;
import net.hypixel.nerdbot.util.exception.RepositoryException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class RepositoryManager {

    private final Map<Class<?>, Object> repositories = new HashMap<>();

    @SneakyThrows
    public <T> T getRepository(Class<T> repositoryClass) {
        if (!repositories.containsKey(repositoryClass)) {
            throw new RepositoryException("Repository not registered: " + repositoryClass.getName());
        }

        return repositoryClass.cast(repositories.get(repositoryClass));
    }

    public void registerRepositoriesFromPackage(String packageName, MongoClient mongoClient, String databaseName) throws RepositoryException {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Class<?>[] classes = ClassUtil.getClasses(packageName, classLoader);

            for (Class<?> clazz : classes) {
                if (isRepository(clazz)) {
                    if (!repositories.containsKey(clazz)) {
                        Object repositoryInstance = createRepositoryInstance(clazz, mongoClient, databaseName);
                        repositories.put(clazz, repositoryInstance);
                    }
                }
            }
        } catch (Exception e) {
            throw new RepositoryException("Failed to register repositories from package: " + packageName, e);
        }
    }

    private boolean isRepository(Class<?> clazz) {
        return CachedMongoRepository.class.isAssignableFrom(clazz);
    }

    private <T> T createRepositoryInstance(Class<T> repositoryClass, MongoClient mongoClient, String databaseName) throws RepositoryException {
        try {
            Constructor<T> constructor = repositoryClass.getDeclaredConstructor(MongoClient.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(mongoClient, databaseName);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RepositoryException("Failed to create repository instance: " + repositoryClass.getName() + " - No constructor found with parameters: MongoClient, String", e);
        }
    }
}
