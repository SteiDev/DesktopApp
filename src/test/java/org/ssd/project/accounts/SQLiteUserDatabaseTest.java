package org.ssd.project.accounts;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConfig;

/**
 * Tests for account handling: constructor, saveUser, login and deleteUser. Stats behaviour lives in
 * {@link SQLiteUserDatabaseStatsTest}.
 */
class SQLiteUserDatabaseTest {

    private static final String USERNAME = "alice";
    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "S3cret!pass";

    @TempDir Path tempDir;

    private Path dbFile;
    private SQLiteUserDatabase db;

    @BeforeEach
    void setUp() {
        dbFile = tempDir.resolve("test-accounts.db");
        db = new SQLiteUserDatabase(dbFile.toString());
    }

    /* ---------- helpers ---------- */

    private Connection rawConnection() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile, config.toProperties());
    }

    private int count(String table) throws SQLException {
        try (Connection conn = rawConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private User saveAlice() {
        return db.saveUser(USERNAME, EMAIL, PASSWORD).orElseThrow();
    }

    /* ---------- constructor ---------- */

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("creates missing parent directories and the database file")
        void createsMissingParentDirectories() {
            Path nested = tempDir.resolve("a").resolve("b").resolve("db.sqlite");

            new SQLiteUserDatabase(nested.toString());

            assertTrue(Files.isRegularFile(nested));
        }

        @Test
        @DisplayName("throws IllegalStateException when the parent directory cannot be created")
        void unusableParent_throws() throws IOException {
            /* A regular file sits where the parent directory should be */
            Path blocker = Files.createFile(tempDir.resolve("not-a-dir"));
            String invalid = blocker.resolve("db.sqlite").toString();

            assertThrows(IllegalStateException.class, () -> new SQLiteUserDatabase(invalid));
        }

        @Test
        @DisplayName("reopening an existing database keeps its data and constraints")
        void reopen_keepsData() throws SQLException {
            saveAlice();

            SQLiteUserDatabase reopened = new SQLiteUserDatabase(dbFile.toString());

            assertAll(
                    () -> assertTrue(reopened.login(EMAIL, PASSWORD).isPresent()),
                    () -> assertTrue(reopened.saveUser("other", EMAIL, PASSWORD).isEmpty()),
                    () -> assertEquals(1, count("users")));
        }
    }

    /* ---------- saveUser ---------- */

    @Nested
    @DisplayName("saveUser")
    class SaveUser {

        @Test
        @DisplayName("stores a new user and returns it")
        void newUser_returnsUser() {
            Optional<User> saved = db.saveUser(USERNAME, EMAIL, PASSWORD);

            assertTrue(saved.isPresent());
            assertAll(
                    () -> assertEquals(USERNAME, saved.get().username()),
                    () -> assertEquals(EMAIL, saved.get().email()),
                    () -> assertTrue(saved.get().id() > 0));
        }

        @Test
        @DisplayName("stores a BCrypt hash, never the raw password")
        void passwordIsHashed() {
            User saved = saveAlice();

            assertAll(
                    () -> assertNotEquals(PASSWORD, saved.passwordHash()),
                    () -> assertTrue(saved.passwordHash().startsWith("$2")));
        }

        @Test
        @DisplayName("trims and lowercases the email")
        void normalizesEmail() {
            User saved = db.saveUser(USERNAME, "  Alice@Example.COM  ", PASSWORD).orElseThrow();

            assertEquals("alice@example.com", saved.email());
        }

        @Test
        @DisplayName("assigns distinct, increasing ids")
        void increasingIds() {
            User first = db.saveUser("a", "a@example.com", PASSWORD).orElseThrow();
            User second = db.saveUser("b", "b@example.com", PASSWORD).orElseThrow();

            assertTrue(second.id() > first.id());
        }

        @Test
        @DisplayName("allows the same username with different emails")
        void sameUsernameDifferentEmail_succeeds() {
            saveAlice();

            assertTrue(db.saveUser(USERNAME, "alice2@example.com", PASSWORD).isPresent());
        }

        @Test
        @DisplayName("rejects a duplicate email")
        void duplicateEmail_returnsEmpty() {
            saveAlice();

            assertTrue(db.saveUser("bob", EMAIL, "otherPass1").isEmpty());
        }

        @Test
        @DisplayName("treats emails as duplicates regardless of case and whitespace")
        void duplicateEmailDifferentCase_returnsEmpty() {
            saveAlice();

            assertTrue(db.saveUser("bob", "  ALICE@EXAMPLE.COM ", "otherPass1").isEmpty());
        }

        @Test
        @DisplayName("rejects a null or blank username")
        void invalidUsername_throws() throws SQLException {
            assertAll(
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> db.saveUser(null, EMAIL, PASSWORD)),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> db.saveUser("   ", EMAIL, PASSWORD)));

            assertEquals(0, count("users"));
        }

        @Test
        @DisplayName("rejects a null or blank email")
        void invalidEmail_throws() throws SQLException {
            assertAll(
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> db.saveUser(USERNAME, null, PASSWORD)),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> db.saveUser(USERNAME, "  ", PASSWORD)));

            assertEquals(0, count("users"));
        }

        @Test
        @DisplayName("treats SQL metacharacters in the input as plain data")
        void sqlMetacharacters_areStoredVerbatim() throws SQLException {
            String nastyName = "Robert'); DROP TABLE users;--";
            String nastyEmail = "x'; DROP TABLE users; --@example.com";

            User saved = db.saveUser(nastyName, nastyEmail, PASSWORD).orElseThrow();

            assertAll(
                    () -> assertEquals(nastyName, saved.username()),
                    () -> assertEquals(1, count("users")),
                    () -> assertTrue(db.login(nastyEmail, PASSWORD).isPresent()));
        }
    }

    /* ---------- login ---------- */

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("succeeds with correct credentials")
        void validCredentials_returnsUser() {
            User saved = saveAlice();

            Optional<User> result = db.login(EMAIL, PASSWORD);

            assertTrue(result.isPresent());
            assertAll(
                    () -> assertEquals(saved.id(), result.get().id()),
                    () -> assertEquals(USERNAME, result.get().username()),
                    () -> assertEquals(EMAIL, result.get().email()));
        }

        @Test
        @DisplayName("ignores email case and surrounding whitespace")
        void emailIsNormalized() {
            saveAlice();

            assertTrue(db.login("  ALICE@Example.com ", PASSWORD).isPresent());
        }

        @Test
        @DisplayName("fails with a wrong password")
        void wrongPassword_returnsEmpty() {
            saveAlice();

            assertTrue(db.login(EMAIL, "wrong-password").isEmpty());
        }

        @Test
        @DisplayName("treats the password as case-sensitive")
        void passwordIsCaseSensitive() {
            saveAlice();

            assertTrue(db.login(EMAIL, PASSWORD.toUpperCase()).isEmpty());
        }

        @Test
        @DisplayName("fails for an unknown email")
        void unknownEmail_returnsEmpty() {
            saveAlice();

            assertTrue(db.login("nobody@example.com", PASSWORD).isEmpty());
        }

        @Test
        @DisplayName("fails when the password is null")
        void nullPassword_returnsEmpty() {
            saveAlice();

            assertTrue(db.login(EMAIL, null).isEmpty());
        }

        @Test
        @DisplayName("fails when the email is null")
        void nullEmail_returnsEmpty() {
            saveAlice();

            assertTrue(db.login(null, PASSWORD).isEmpty());
        }

        @Test
        @DisplayName("returns empty on an empty database")
        void emptyDatabase_returnsEmpty() {
            assertTrue(db.login(EMAIL, PASSWORD).isEmpty());
        }

        @Test
        @DisplayName("picks the right user when several exist")
        void multipleUsers_returnsMatchingUser() {
            db.saveUser("alice", "alice@example.com", "alicePass1");
            User bob = db.saveUser("bob", "bob@example.com", "bobPass22").orElseThrow();

            Optional<User> result = db.login("bob@example.com", "bobPass22");

            assertTrue(result.isPresent());
            assertEquals(bob.id(), result.get().id());
            assertTrue(db.login("bob@example.com", "alicePass1").isEmpty());
        }

        @Test
        @DisplayName("is not fooled by SQL injection in the email")
        void sqlInjectionInEmail_returnsEmpty() {
            saveAlice();

            assertAll(
                    () -> assertTrue(db.login("' OR '1'='1", PASSWORD).isEmpty()),
                    () ->
                            assertTrue(
                                    db.login("nobody@example.com' OR '1'='1", PASSWORD).isEmpty()));
        }
    }

    /* ---------- deleteUser ---------- */

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("returns true and removes the user")
        void existingId_returnsTrueAndRemovesUser() throws SQLException {
            User saved = saveAlice();

            assertTrue(db.deleteUser(saved.id()));

            assertAll(
                    () -> assertTrue(db.login(EMAIL, PASSWORD).isEmpty()),
                    () -> assertEquals(0, count("users")));
        }

        @Test
        @DisplayName("returns false for an unknown or invalid id")
        void unknownId_returnsFalse() {
            assertAll(
                    () -> assertFalse(db.deleteUser(9999L)),
                    () -> assertFalse(db.deleteUser(0L)),
                    () -> assertFalse(db.deleteUser(-1L)));
        }

        @Test
        @DisplayName("returns false the second time for the same id")
        void calledTwice_secondCallReturnsFalse() {
            User saved = saveAlice();

            assertTrue(db.deleteUser(saved.id()));
            assertFalse(db.deleteUser(saved.id()));
        }

        @Test
        @DisplayName("only removes the target user")
        void onlyRemovesTheTargetUser() {
            User a = db.saveUser("a", "a@example.com", PASSWORD).orElseThrow();
            db.saveUser("b", "b@example.com", PASSWORD);

            db.deleteUser(a.id());

            assertAll(
                    () -> assertTrue(db.login("a@example.com", PASSWORD).isEmpty()),
                    () -> assertTrue(db.login("b@example.com", PASSWORD).isPresent()));
        }

        @Test
        @DisplayName("frees the email for re-registration")
        void freesEmailForReRegistration() {
            User saved = saveAlice();
            db.deleteUser(saved.id());

            assertTrue(db.saveUser(USERNAME, EMAIL, PASSWORD).isPresent());
        }
    }
}
