package net.hypixel.skyblocknerds.database.repository;

import com.mongodb.client.MongoClient;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.hypixel.skyblocknerds.database.repository.exception.RepositoryException;
import net.hypixel.skyblocknerds.utilities.ClassUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Getter
@Log4j2
public class RepositoryManager {

    @Getter
    private static final RepositoryManager instance = new RepositoryManager();
    private final Map<Class<?>, Object> repositories = new HashMap<>();

    /**
     * Get a {@link Repository} by its class
     *
     * @param repositoryClass The class of the {@link Repository}
     * @param <T>             The type of the {@link Repository}
     *
     * @return The {@link Repository} instance
     */
    @SneakyThrows
    public <T> T getRepository(Class<T> repositoryClass) {
        if (!repositories.containsKey(repositoryClass)) {
            throw new RepositoryException("Repository not registered: " + repositoryClass.getName());
        }

        return repositoryClass.cast(repositories.get(repositoryClass));
    }

    /**
     * Get a {@link Repository} by its name
     *
     * @param repositoryName The name of the {@link Repository}
     *
     * @return The {@link Repository} instance
     *
     * @throws RepositoryException If the {@link Repository} is not registered
     */
    public Repository<?> getRepository(String repositoryName) throws RepositoryException {
        Map.Entry<Class<?>, Object> entry = repositories.entrySet().stream()
            .filter(e -> e.getKey().getSimpleName().equalsIgnoreCase(repositoryName))
            .findFirst()
            .orElseThrow(() -> new RepositoryException("Repository not registered: " + repositoryName));

        return (Repository<?>) entry.getValue();
    }

    /**
     * Register a {@link Repository} by its class with the given {@link MongoClient} and database name
     *
     * @param repositoryClass The class of the {@link Repository}
     * @param mongoClient     The {@link MongoClient} instance to use
     * @param databaseName    The name of the database
     *
     * @throws RepositoryException If the {@link Repository} is already registered
     */
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

    /**
     * Register all {@link Repository} classes in the given package with the given {@link MongoClient} and database name
     *
     * @param packageName  The package name to scan for {@link Repository} classes
     * @param mongoClient  The {@link MongoClient} instance to use
     * @param databaseName The name of the database
     *
     * @throws RepositoryException If an error occurs while registering the {@link Repository} classes
     */
    public void registerRepositoriesFromPackage(String packageName, MongoClient mongoClient, String databaseName) throws RepositoryException {
        log.info("Registering repositories from package: " + packageName);

        try {
            Set<Class<?>> classes = ClassUtils.findClasses(packageName, Repository.class);

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

    /**
     * Check if the given class is a {@link Repository}
     *
     * @param clazz The class to check
     *
     * @return {@code true} if the class is a {@link Repository}, otherwise {@code false}
     */
    private boolean isRepository(Class<?> clazz) {
        return Repository.class.isAssignableFrom(clazz);
    }

    /**
     * Create a new instance of the given {@link Repository} class with the given {@link MongoClient} and database name
     *
     * @param repositoryClass The class of the {@link Repository}
     * @param mongoClient     The {@link MongoClient} instance to use
     * @param databaseName    The name of the database
     * @param <T>             The type of the {@link Repository}
     *
     * @return The new instance of the {@link Repository}
     *
     * @throws RepositoryException If an error occurs while creating the instance
     */
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

