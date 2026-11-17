package org.ssd.project.accounts;

import java.sql.*;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;
import java.util.Set;
import java.util.Map;

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
        this.url = "jdbc:sqlite:" + dbPath;

        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);
        configProperties = config.toProperties();

        log.debug("Initializing SQLite repository at {}", url);

        initSchemas();
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

    @Override
    public Optional<User> saveUser(String username, String email, String rawPassword) {
        String sql = "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)";
        String normalizedEmail = normalizeEmail(email);
        String passwordHash = passwordService.hash(rawPassword);

        try (Connection conn = connect();
                PreparedStatement ps =
                        conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, normalizedEmail);
            ps.setString(3, passwordHash);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                long id = keys.next() ? keys.getLong(1) : 0;
                log.info("Saved new user id={}", id);
                return Optional.of(new User(id, username, normalizedEmail, passwordHash));
            }
        } catch (SQLException e) {
            /* The email must be unique */
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("UNIQUE constraint failed: users.email")) {
                log.warn("Insert rejected by UNIQUE constraint on email");
                return Optional.empty();
            }
            log.error("Database error while saving user: {}", e.toString());
            throw new IllegalStateException("Database error while saving user", e);
        }
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
                "UPDATE " + table + " SET " + field + " = ? WHERE " + KEY_COLUMNS.get(table) + " = ?";

        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
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
        String sql = "SELECT " + field + " FROM " + table + " WHERE " + KEY_COLUMNS.get(table) + " = ?";

        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
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
            throw new IllegalArgumentException("Column not allowed: " + table + "." + field);
        }
    }
}
