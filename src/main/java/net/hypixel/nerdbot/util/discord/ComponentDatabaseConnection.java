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
        String uri = System.getProperty("db.postgres.uri", "jdbc:postgresql://username:password@localhost:5432/skyblock_nerds");

        PGSimpleDataSource pgSource = new PGSimpleDataSource();
        pgSource.setUrl(uri);

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
