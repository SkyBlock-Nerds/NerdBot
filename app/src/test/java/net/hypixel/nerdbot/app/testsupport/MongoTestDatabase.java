package net.hypixel.nerdbot.app.testsupport;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.UUID;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * One real MongoDB in Docker, shared by every integration test in the run. Each test gets its own
 * database name so tests can't see each other's documents. Without Docker the tests are skipped
 * rather than failed, so a local build still works.
 */
public final class MongoTestDatabase {

    private static MongoDBContainer container;
    private static MongoClient client;

    private MongoTestDatabase() {
    }

    public static synchronized MongoClient client() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker isn't available, skipping MongoDB integration tests");

        if (client == null) {
            container = new MongoDBContainer("mongo:7.0");
            container.start();
            client = MongoClients.create(container.getConnectionString());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                client.close();
                container.stop();
            }));
        }
        return client;
    }

    /** For code that builds its own client, such as Marmalade's Database. */
    public static String connectionString() {
        client();
        return container.getConnectionString();
    }

    public static String freshDatabaseName() {
        return "test_" + UUID.randomUUID().toString().replace("-", "");
    }
}
