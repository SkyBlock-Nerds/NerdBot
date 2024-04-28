package net.hypixel.skyblocknerds.database.sql;

import com.zaxxer.hikari.HikariDataSource;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class DiscordComponentDatabase {
    private final HikariDataSource source;

    public DiscordComponentDatabase() throws SQLException {
        // DatabaseConfig databaseConfig = ConfigurationManager.loadConfig(DatabaseConfig.class);

        final PGSimpleDataSource pgSource = new PGSimpleDataSource();
        pgSource.setServerNames(new String[]{"localhost"});
        pgSource.setPortNumbers(new int[]{5432});
        pgSource.setUser("postgres");
        pgSource.setPassword("password");
        pgSource.setDatabaseName("postgres");

        source = new HikariDataSource();
        source.setDataSource(pgSource);

        source.getConnection().close();
    }

    public Connection getConnection() {
        try {
            return source.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to get an SQL connection", e);
        }
    }
}
