package org.ssd.project.accounts;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for the database setup: directories, schema, constraints and the stats trigger. */
class SQLiteDatabaseTest {

    @TempDir Path tempDir;

    private Path dbFile;
    private SQLiteDatabase database;

    @BeforeEach
    void setUp() {
        dbFile = tempDir.resolve("test-accounts.db");
        database = new SQLiteDatabase(dbFile.toString());
    }

    /* ---------- helpers ---------- */

    private void executeRaw(String sql) throws SQLException {
        try (Connection conn = database.connect();
                Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private int count(String table) throws SQLException {
        try (Connection conn = database.connect();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /* ---------- constructor ---------- */

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("creates missing parent directories and the database file")
        void createsMissingParentDirectories() {
            Path nested = tempDir.resolve("a").resolve("b").resolve("db.sqlite");

            new SQLiteDatabase(nested.toString());

            assertTrue(Files.isRegularFile(nested));
        }

        @Test
        @DisplayName("throws IllegalStateException when the parent directory cannot be created")
        void unusableParent_throws() throws IOException {
            /* A regular file sits where the parent directory should be */
            Path blocker = Files.createFile(tempDir.resolve("not-a-dir"));
            String invalid = blocker.resolve("db.sqlite").toString();

            assertThrows(IllegalStateException.class, () -> new SQLiteDatabase(invalid));
        }

        @Test
        @DisplayName("reopening an existing database keeps its data and constraints")
        void reopen_keepsDataAndConstraints() throws SQLException {
            new SQLiteUserRepository(database).create("alice", "alice@example.com", "hash");

            SQLiteDatabase reopened = new SQLiteDatabase(dbFile.toString());
            IUserRepository reopenedUsers = new SQLiteUserRepository(reopened);

            assertAll(
                    () -> assertTrue(reopenedUsers.findByEmail("alice@example.com").isPresent()),
                    () ->
                            assertTrue(
                                    reopenedUsers
                                            .create("other", "alice@example.com", "hash")
                                            .isEmpty()),
                    () -> assertEquals(1, count("users")),
                    () -> assertEquals(1, count("stats")));
        }
    }

    /* ---------- schema ---------- */

    @Nested
    @DisplayName("schema")
    class Schema {

        @Test
        @DisplayName("inserting a user creates a stats row through the trigger")
        void trigger_createsStatsRow() throws SQLException {
            executeRaw(
                    "INSERT INTO users (username, email, password_hash) "
                            + "VALUES ('alice', 'alice@example.com', 'hash')");

            assertAll(() -> assertEquals(1, count("users")), () -> assertEquals(1, count("stats")));
        }

        @Test
        @DisplayName("the schema rejects a stats row for an unknown user (foreign key)")
        void foreignKey_rejectsUnknownUser() {
            assertThrows(
                    SQLException.class,
                    () -> executeRaw("INSERT INTO stats (user_id) VALUES (9999)"));
        }

        @Test
        @DisplayName("the schema rejects duplicate emails that differ only by case")
        void uniqueEmail_isCaseInsensitive() throws SQLException {
            executeRaw(
                    "INSERT INTO users (username, email, password_hash) "
                            + "VALUES ('a', 'alice@example.com', 'hash')");

            assertThrows(
                    SQLException.class,
                    () ->
                            executeRaw(
                                    "INSERT INTO users (username, email, password_hash) "
                                            + "VALUES ('b', 'ALICE@EXAMPLE.COM', 'hash')"));
        }
    }
}
