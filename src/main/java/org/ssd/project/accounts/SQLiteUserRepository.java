package org.ssd.project.accounts;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

public final class SQLiteUserRepository implements IUserRepository {
    private static final Logger log = LoggerFactory.getLogger(SQLiteUserRepository.class);

    private static final String INSERT_USER =
            "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)";
    private static final String SELECT_BY_EMAIL =
            "SELECT id, username, email, password_hash FROM users WHERE email = ?";
    private static final String DELETE_BY_ID = "DELETE FROM users WHERE id = ?";
    private static final String UPDATE_USERNAME =
            "UPDATE users SET username = ? WHERE id = ? "
                    + "RETURNING id, username, email, password_hash";
    private static final String UPDATE_PASSWORD_HASH =
            "UPDATE users SET password_hash = ? WHERE id = ? "
                    + "RETURNING id, username, email, password_hash";

    private static final String SELECT_BY_ID =
            "SELECT id, username, email, password_hash FROM users WHERE id = ?";

    private final SQLiteDatabase db;

    public SQLiteUserRepository(SQLiteDatabase db) {
        this.db = db;
    }

    @Override
    public Optional<User> create(String username, String email, String passwordHash) {
        try (Connection conn = db.connect();
                PreparedStatement ps =
                        conn.prepareStatement(INSERT_USER, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated id returned for new user");
                }
                long id = keys.getLong(1);
                log.info("Saved new user id={}", id);
                return Optional.of(new User(id, username, email, passwordHash));
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
    public Optional<User> findByEmail(String email) {
        try (Connection conn = db.connect();
                PreparedStatement ps = conn.prepareStatement(SELECT_BY_EMAIL)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(
                        new User(
                                rs.getLong("id"),
                                rs.getString("username"),
                                rs.getString("email"),
                                rs.getString("password_hash")));
            }
        } catch (SQLException e) {
            log.error("Database error while looking up user: {}", e.toString());
            throw new IllegalStateException("Database error while looking up user", e);
        }
    }

    @Override
    public boolean deleteById(long id) {
        try (Connection conn = db.connect();
                PreparedStatement ps = conn.prepareStatement(DELETE_BY_ID)) {
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

    @Override
    public Optional<User> updateUsername(long id, String username) {
        try (Connection conn = db.connect();
                PreparedStatement ps = conn.prepareStatement(UPDATE_USERNAME)) {
            ps.setString(1, username);
            ps.setLong(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                Optional<User> updated = rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
                log.info("Update username for user id={} (updated={})", id, updated.isPresent());
                return updated;
            }
        } catch (SQLException e) {
            log.error("Database error while updating username for id={}: {}", id, e.toString());
            throw new IllegalStateException("Database error while updating username", e);
        }
    }

    private static User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("email"),
                rs.getString("password_hash"));
    }

    @Override
    public Optional<User> updatePasswordHash(long id, String passwordHash) {
        try (Connection conn = db.connect();
                PreparedStatement ps = conn.prepareStatement(UPDATE_PASSWORD_HASH)) {
            ps.setString(1, passwordHash);
            ps.setLong(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                Optional<User> updated = rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
                log.info("Update password for user id={} (updated={})", id, updated.isPresent());
                return updated;
            }
        } catch (SQLException e) {
            log.error("Database error while updating password for id={}: {}", id, e.toString());
            throw new IllegalStateException("Database error while updating password", e);
        }
    }

    @Override
    public Optional<User> findById(long id) {
        try (Connection conn = db.connect();
                PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            log.error("Database error while looking up user id={}: {}", id, e.toString());
            throw new IllegalStateException("Database error while looking up user", e);
        }
    }
}
