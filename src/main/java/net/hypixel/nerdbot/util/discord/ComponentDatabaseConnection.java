package net.hypixel.nerdbot.util.discord;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.SQLException;

@Slf4j
public class ComponentDatabaseConnection {

    private final HikariDataSource source;

    public ComponentDatabaseConnection() throws SQLException {
        final PGSimpleDataSource pgSource = new PGSimpleDataSource();
        pgSource.setServerNames(new String[]{System.getProperty("db.postgres.host", "localhost")});
        pgSource.setPortNumbers(new int[]{Integer.parseInt(System.getProperty("db.postgres.port", "5432"))});
        pgSource.setUser(System.getProperty("db.postgres.user", "postgres"));
        pgSource.setPassword(System.getProperty("db.postgres.password", "password"));
        pgSource.setDatabaseName(System.getProperty("db.postgres.database", "postgres"));

        source = new HikariDataSource();
        source.setDataSource(pgSource);

        source.getConnection().close();
    }

    public Connection getConnection() {
        try {
            return source.getConnection();
        } catch (SQLException exception) {
            log.error("Failed to get connection from pool", exception);
            return null;
        }
    }
}
