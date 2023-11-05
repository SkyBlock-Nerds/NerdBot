package net.hypixel.nerdbot.api.repository;

import com.mongodb.client.MongoClient;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.hypixel.nerdbot.util.ClassUtil;
import net.hypixel.nerdbot.util.exception.RepositoryException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Log4j2
public class RepositoryManager {

    private final Map<Class<?>, Object> repositories = new HashMap<>();

    @SneakyThrows
    public <T> T getRepository(Class<T> repositoryClass) {
        if (!repositories.containsKey(repositoryClass)) {
            throw new RepositoryException("Repository not registered: " + repositoryClass.getName());
        }

        return repositoryClass.cast(repositories.get(repositoryClass));
    }

    public <T> T getRepository(String repositoryName) throws RepositoryException {
        Map.Entry<Class<?>, Object> entry = repositories.entrySet().stream()
            .filter(e -> e.getKey().getSimpleName().equalsIgnoreCase(repositoryName))
            .findFirst()
            .orElseThrow(() -> new RepositoryException("Repository not registered: " + repositoryName));

        Repository<?> repository = (Repository<?>) entry.getValue();
        return (T) repository;
    }

    public void registerRepositoriesFromPackage(String packageName, MongoClient mongoClient, String databaseName) throws RepositoryException {
        log.info("Registering repositories from package: " + packageName);

        try {
            Set<Class<?>> classes = ClassUtil.findClasses(packageName, Repository.class);

            for (Class<?> clazz : classes) {
                log.debug("Found class: " + clazz.getName());

                if (isRepository(clazz) && !repositories.containsKey(clazz)) {
                    Object repositoryInstance = createRepositoryInstance(clazz, mongoClient, databaseName);
                    repositories.put(clazz, repositoryInstance);
                    log.info("Registered repository: " + clazz.getName());
                }
            }
        } catch (Exception e) {
            throw new RepositoryException("Failed to register repositories from package: " + packageName, e);
        }
    }

    private boolean isRepository(Class<?> clazz) {
        return Repository.class.isAssignableFrom(clazz);
    }

    private <T> T createRepositoryInstance(Class<T> repositoryClass, MongoClient mongoClient, String databaseName) throws RepositoryException {
        try {
            Constructor<T> constructor = repositoryClass.getDeclaredConstructor(MongoClient.class, String.class);
            return constructor.newInstance(mongoClient, databaseName);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new RepositoryException("Failed to create repository instance: " + repositoryClass.getName(), e);
        }
    }
}
