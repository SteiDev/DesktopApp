package org.ssd.project.accounts;

import java.sql.*;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;

public final class SQLiteUserDatabase implements IUserDatabase {
    private static final Logger log = LoggerFactory.getLogger(SQLiteUserDatabase.class);
    private final IPasswordService passwordService = new BCryptPasswordService();

    private final String url;
    private final Properties configProperties;

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

    /* Trims and makes the email lowercase */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

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
}
