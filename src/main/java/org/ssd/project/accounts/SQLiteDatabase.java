package org.ssd.project.accounts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;

/** Owns the SQLite connection settings and the schema. Shared by all SQLite repositories. */
public final class SQLiteDatabase {
    private static final Logger log = LoggerFactory.getLogger(SQLiteDatabase.class);

    private static final String CREATE_USERS =
            """
            CREATE TABLE IF NOT EXISTS users (
                id            INTEGER PRIMARY KEY AUTOINCREMENT,
                username      TEXT NOT NULL,
                email         TEXT NOT NULL UNIQUE COLLATE NOCASE,
                password_hash TEXT NOT NULL
            )
            """;

    private static final String CREATE_STATS =
            """
            CREATE TABLE IF NOT EXISTS stats (
                user_id           INTEGER PRIMARY KEY,
                quizzes_completed INTEGER NOT NULL DEFAULT 0,
                accuracy          REAL NOT NULL DEFAULT 0.0,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """;

    /* Every new user gets a stats row in the same transaction as the user insert */
    private static final String CREATE_STATS_TRIGGER =
            """
            CREATE TRIGGER IF NOT EXISTS create_stats_for_new_user
            AFTER INSERT ON users
            BEGIN
                INSERT INTO stats (user_id) VALUES (NEW.id);
            END
            """;

    private final String url;
    private final Properties configProperties;

    public SQLiteDatabase() {
        this("data/accounts.db");
    }

    public SQLiteDatabase(String dbPath) {
        createParentDirectories(dbPath);

        this.url = "jdbc:sqlite:" + dbPath;

        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);
        this.configProperties = config.toProperties();

        log.debug("Initializing SQLite database at {}", url);
        initSchema();
    }

    /** Opens a new connection. Callers must close it. */
    Connection connect() throws SQLException {
        return DriverManager.getConnection(url, configProperties);
    }

    /* SQLite creates the file but not the folders leading to it */
    private static void createParentDirectories(String dbPath) {
        Path parent = Path.of(dbPath).toAbsolutePath().getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            log.error("Could not create database directory {}", parent, e);
            throw new IllegalStateException("Could not create database directory " + parent, e);
        }
    }

    /* All schemas are initialized here */
    private void initSchema() {
        try (Connection conn = connect();
                Statement st = conn.createStatement()) {
            st.execute(CREATE_USERS);
            st.execute(CREATE_STATS);
            st.execute(CREATE_STATS_TRIGGER);
            log.info("Database schema ready ({})", url);
        } catch (SQLException e) {
            log.error("Could not initialize database schema", e);
            throw new IllegalStateException("Could not initialize database schema", e);
        }
    }
}
