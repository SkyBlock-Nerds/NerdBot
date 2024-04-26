package net.hypixel.skyblocknerds.database.repository;

import com.mongodb.client.MongoClient;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.hypixel.skyblocknerds.database.repository.exception.RepositoryException;
import net.hypixel.skyblocknerds.utilities.ClassUtilities;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Getter
@Log4j2
public class RepositoryManager {

    private final Map<Class<?>, Object> repositories = new HashMap<>();
    @Getter
    private static final RepositoryManager instance = new RepositoryManager();

    @SneakyThrows
    public <T> T getRepository(Class<T> repositoryClass) {
        if (!repositories.containsKey(repositoryClass)) {
            throw new RepositoryException("Repository not registered: " + repositoryClass.getName());
        }

        return repositoryClass.cast(repositories.get(repositoryClass));
    }

    public Repository<?> getRepository(String repositoryName) throws RepositoryException {
        Map.Entry<Class<?>, Object> entry = repositories.entrySet().stream()
                .filter(e -> e.getKey().getSimpleName().equalsIgnoreCase(repositoryName))
                .findFirst()
                .orElseThrow(() -> new RepositoryException("Repository not registered: " + repositoryName));

        return (Repository<?>) entry.getValue();
    }

    public void registerRepository(Class<? extends Repository<?>> repositoryClass, MongoClient mongoClient, String databaseName) throws RepositoryException {
        log.info("Registering repository: " + repositoryClass.getName());

        if (repositories.containsKey(repositoryClass)) {
            throw new RepositoryException("Repository already registered: " + repositoryClass.getName());
        }

        if (isRepository(repositoryClass)) {
            Object repositoryInstance = createRepositoryInstance(repositoryClass, mongoClient, databaseName);
            repositories.put(repositoryClass, repositoryInstance);
            log.info("Registered repository: " + repositoryClass.getName());
        }
    }

    public void registerRepositoriesFromPackage(String packageName, MongoClient mongoClient, String databaseName) throws RepositoryException {
        log.info("Registering repositories from package: " + packageName);

        try {
            Set<Class<?>> classes = ClassUtilities.findClasses(packageName, Repository.class);

            for (Class<?> clazz : classes) {
                log.debug("Found class: " + clazz.getName());

                if (isRepository(clazz) && !repositories.containsKey(clazz)) {
                    Object repositoryInstance = createRepositoryInstance(clazz, mongoClient, databaseName);
                    repositories.put(clazz, repositoryInstance);
                    log.info("Registered repository: " + clazz.getName());
                }
            }
        } catch (Exception exception) {
            throw new RepositoryException("Failed to register repositories from package: " + packageName, exception);
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
                 InvocationTargetException exception) {
            throw new RepositoryException("Failed to create repository instance: " + repositoryClass.getName(), exception);
        }
    }
}

