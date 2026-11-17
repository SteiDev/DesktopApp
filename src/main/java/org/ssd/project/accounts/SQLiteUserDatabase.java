package org.ssd.project.accounts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

public final class SQLiteUserDatabase implements IUserDatabase {
    private static final Logger log = LoggerFactory.getLogger(SQLiteUserDatabase.class);
    private final IPasswordService passwordService = new BCryptPasswordService();

    private final String url;
    private final Properties configProperties;

    /* Fields that can be get/set from updateField and getField */
    private static final Map<String, Set<String>> ALLOWED_COLUMNS =
            Map.of(
                    "users", Set.of("username"),
                    "stats", Set.of("quizzes_completed", "accuracy"));
    /* Table -> id for the table mapping */
    private static final Map<String, String> KEY_COLUMNS =
            Map.of("users", "id", "stats", "user_id");

    /* Default constructor */
    public SQLiteUserDatabase() {
        this("data/accounts.db");
    }

    public SQLiteUserDatabase(String dbPath) {
        createParentDirectories(dbPath);

        this.url = "jdbc:sqlite:" + dbPath;

        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);
        configProperties = config.toProperties();

        log.debug("Initializing SQLite repository at {}", url);

        initSchemas();
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

    /* All schemes should be initialized here */
    private void initSchemas() {
        createUsersTable();
        createStatsTable();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, configProperties);
    }

    private void createUsersTable() {
        String sql =
                """
            CREATE TABLE IF NOT EXISTS users (
            id   INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL,
            email TEXT NOT NULL UNIQUE COLLATE NOCASE,
            password_hash TEXT NOT NULL
            )
            """;
        try (Connection conn = connect();
                Statement st = conn.createStatement()) {
            st.execute(sql);
            log.info("Users table schema ready ({})", url);
        } catch (SQLException e) {
            log.error("Could not initialize users table schema", e);
            throw new IllegalStateException("Could not initialize users table schema", e);
        }
    }

    private void createStatsTable() {
        String sql =
                """
                        CREATE TABLE IF NOT EXISTS stats (
                        user_id INTEGER PRIMARY KEY,
                        quizzes_completed INTEGER NOT NULL DEFAULT 0,
                        accuracy REAL NOT NULL DEFAULT 0.0,
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                        )
                        """;
        try (Connection conn = connect();
                Statement st = conn.createStatement()) {
            st.execute(sql);
            log.info("Stats table schema ready ({})", url);
        } catch (SQLException e) {
            log.error("Could not initialize stats table schema", e);
            throw new IllegalStateException("Could not initialize users table schema", e);
        }
    }

    /* Trims and makes the email lowercase */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            log.warn("Rejected blank or null {}", name);
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }

    @Override
    public Optional<User> saveUser(String username, String email, String rawPassword) {
        requireNonBlank(username, "username");
        requireNonBlank(email, "email");

        String userSql = "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)";
        String statsSql = "INSERT INTO stats (user_id) VALUES (?)";
        String normalizedEmail = normalizeEmail(email);
        String passwordHash = passwordService.hash(rawPassword);

        try (Connection conn = connect()) {
            /* The user and their stats row are created together or not at all */
            conn.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps =
                        conn.prepareStatement(userSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, username);
                    ps.setString(2, normalizedEmail);
                    ps.setString(3, passwordHash);
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("No generated id returned for new user");
                        }
                        id = keys.getLong(1);
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(statsSql)) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }

                conn.commit();
                log.info("Saved new user id={}", id);
                return Optional.of(new User(id, username, normalizedEmail, passwordHash));
            } catch (SQLException e) {
                log.error("SQL error when trying to save user: {}", e.toString());
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            /* The email must be unique */
            if (isUniqueViolation(e)) {
                log.warn("Insert rejected by UNIQUE constraint on email");
                return Optional.empty();
            }
            log.error("Database error while saving user: {}", e.toString());
            throw new IllegalStateException("Database error while saving user", e);
        }
    }

    /* users.email is the only UNIQUE column besides the primary key */
    private static boolean isUniqueViolation(SQLException e) {
        return e instanceof SQLiteException sqliteException
                && sqliteException.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE;
    }

    @Override
    public Optional<User> login(String email, String rawPassword) {
        String sql = "SELECT id, username, email, password_hash FROM users WHERE email = ?";

        try (Connection conn = connect();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizeEmail(email));
            try (ResultSet rs = ps.executeQuery()) {
                /* No account with that email exists */
                if (!rs.next()) {
                    log.warn("Failed login: unknown account");
                    return Optional.empty();
                }
                long id = rs.getLong("id");
                String hash = rs.getString("password_hash");

                /* Passwords not matching */
                if (rawPassword == null || !passwordService.verify(rawPassword, hash)) {
                    log.warn("Failed login: wrong password for user id={}", id);
                    return Optional.empty();
                }
                log.info("Login OK for user id={}", id);
                return Optional.of(
                        new User(id, rs.getString("username"), rs.getString("email"), hash));
            }
        } catch (SQLException e) {
            log.error("Database error during login: {}", e.toString());
            throw new IllegalStateException("Database error during login", e);
        }
    }

    @Override
    public boolean deleteUser(long id) {
        String sql = "DELETE FROM users WHERE id = ?";

        try (Connection conn = connect();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            boolean deleted = ps.executeUpdate() > 0;
            if (deleted) {
                log.info("Deleted user id={}", id);
            } else {
                log.warn("Delete failed: no user with id={}", id);
            }
            return deleted;
        } catch (SQLException e) {
            log.error("Database error while deleting user id={}: {}", id, e.toString());
            throw new IllegalStateException("Database error while deleting user", e);
        }
    }

    /**
     * Sets a single field of one row to the given value.
     *
     * @param table the table name (must be whitelisted)
     * @param field the column name (must be whitelisted for that table)
     * @param value the new value
     * @param id the id of the user the row belongs to
     * @return {@code true} if a row was updated; {@code false} if no row matched
     */
    private <T> boolean updateField(String table, String field, T value, long id) {
        validateColumn(table, field);
        String sql =
                "UPDATE "
                        + table
                        + " SET "
                        + field
                        + " = ? WHERE "
                        + KEY_COLUMNS.get(table)
                        + " = ?";

        try (Connection conn = connect();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, value);
            ps.setLong(2, id);
            boolean updated = ps.executeUpdate() > 0;
            log.debug("Updated {}.{} for id={} (updated={})", table, field, id, updated);
            return updated;
        } catch (SQLException e) {
            log.error("Database error while updating {}.{}: {}", table, field, e.toString());
            throw new IllegalStateException("Database error while updating " + table, e);
        }
    }

    /**
     * Reads a single field of one row.
     *
     * @param table the table name (must be whitelisted)
     * @param field the column name (must be whitelisted for that table)
     * @param id the id of the user the row belongs to
     * @param type the expected Java type of the value, e.g. {@code Integer.class}
     * @return the value, or empty if no row matched or the value is NULL
     */
    private <T> Optional<T> getField(String table, String field, long id, Class<T> type) {
        validateColumn(table, field);
        String sql =
                "SELECT " + field + " FROM " + table + " WHERE " + KEY_COLUMNS.get(table) + " = ?";

        try (Connection conn = connect();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.ofNullable(rs.getObject(1, type));
            }
        } catch (SQLException e) {
            log.error("Database error while reading {}.{}: {}", table, field, e.toString());
            throw new IllegalStateException("Database error while reading " + table, e);
        }
    }

    /* Table and column names cannot be bound as parameters, so only known ones are allowed */
    private static void validateColumn(String table, String field) {
        Set<String> columns = ALLOWED_COLUMNS.get(table);
        if (columns == null || !columns.contains(field)) {
            log.error("Rejected column {}.{}", table, field);
            throw new IllegalArgumentException("Column not allowed: " + table + "." + field);
        }
    }

    /* Updates a stats field and fails loudly if the user has no stats row */
    private <T> void updateStatsField(String field, T value, long id) {
        if (!updateField("stats", field, value, id)) {
            log.error("Could not update stats.{}: no stats for user id={}", field, id);
            throw new IllegalStateException("No stats for user id=" + id);
        }
    }

    @Override
    public void setQuizzesCompleted(long id, int quizzesCompleted) {
        if (quizzesCompleted < 0) {
            log.warn("Rejected negative quizzesCompleted={} for user id={}", quizzesCompleted, id);
            throw new IllegalArgumentException("quizzesCompleted must not be negative");
        }
        updateStatsField("quizzes_completed", quizzesCompleted, id);
    }

    @Override
    public int getQuizzesCompleted(long id) {
        return getField("stats", "quizzes_completed", id, Integer.class)
                .orElseThrow(
                        () -> {
                            log.error("No quizzes_completed found for user id={}", id);
                            return new IllegalStateException("No stats for user id=" + id);
                        });
    }

    @Override
    public void setAccuracy(long id, double accuracy) {
        if (Double.isNaN(accuracy) || accuracy < 0.0 || accuracy > 1.0) {
            log.warn("Rejected accuracy={} for user id={}", accuracy, id);
            throw new IllegalArgumentException("accuracy must be between 0.0 and 1.0");
        }
        updateStatsField("accuracy", accuracy, id);
    }

    @Override
    public double getAccuracy(long id) {
        return getField("stats", "accuracy", id, Double.class)
                .orElseThrow(
                        () -> {
                            log.error("No accuracy found for user id={}", id);
                            return new IllegalStateException("No stats for user id=" + id);
                        });
    }
}
