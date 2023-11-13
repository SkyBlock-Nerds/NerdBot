package net.hypixel.nerdbot.util.discord;

import com.zaxxer.hikari.HikariDataSource;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.SQLException;

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
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }
}
