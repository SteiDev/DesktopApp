import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConfig;
import org.ssd.project.accounts.SQLiteUserDatabase;
import org.ssd.project.accounts.User;

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

    /* ---------- constructor ---------- */

    @Test
    @DisplayName("constructor throws IllegalStateException when the database cannot be created")
    void constructor_invalidPath_throws() {
        String invalid = tempDir.resolve("missing-dir").resolve("db.sqlite").toString();
        assertThrows(IllegalStateException.class, () -> new SQLiteUserDatabase(invalid));
    }

    @Test
    @DisplayName("constructor can be called twice on the same file (CREATE TABLE IF NOT EXISTS)")
    void constructor_existingDatabase_doesNotFail() {
        new SQLiteUserDatabase(dbFile.toString());
    }

    /* ---------- saveUser ---------- */

    @Test
    @DisplayName("saveUser stores a new user and returns it")
    void saveUser_newUser_returnsUser() {
        Optional<User> saved = db.saveUser(USERNAME, EMAIL, PASSWORD);

        assertTrue(saved.isPresent());
        assertEquals(USERNAME, saved.get().username());
        assertEquals(EMAIL, saved.get().email());
        assertTrue(saved.get().id() > 0);
    }

    @Test
    @DisplayName("saveUser never stores the raw password")
    void saveUser_passwordIsHashed() {
        User saved = db.saveUser(USERNAME, EMAIL, PASSWORD).orElseThrow();

        assertNotEquals(PASSWORD, saved.passwordHash());
        assertFalse(saved.passwordHash().isBlank());
    }

    @Test
    @DisplayName("saveUser trims and lowercases the email")
    void saveUser_normalizesEmail() {
        User saved = db.saveUser(USERNAME, "  Alice@Example.COM  ", PASSWORD).orElseThrow();

        assertEquals("alice@example.com", saved.email());
    }

    @Test
    @DisplayName("saveUser rejects a duplicate email")
    void saveUser_duplicateEmail_returnsEmpty() {
        assertTrue(db.saveUser(USERNAME, EMAIL, PASSWORD).isPresent());

        assertTrue(db.saveUser("bob", EMAIL, "otherPass1").isEmpty());
    }

    @Test
    @DisplayName("saveUser treats emails as duplicates regardless of case and whitespace")
    void saveUser_duplicateEmailDifferentCase_returnsEmpty() {
        assertTrue(db.saveUser(USERNAME, EMAIL, PASSWORD).isPresent());

        assertTrue(db.saveUser("bob", "  ALICE@EXAMPLE.COM ", "otherPass1").isEmpty());
    }

    @Test
    @DisplayName("saveUser allows the same username with different emails")
    void saveUser_sameUsernameDifferentEmail_succeeds() {
        assertTrue(db.saveUser(USERNAME, EMAIL, PASSWORD).isPresent());

        assertTrue(db.saveUser(USERNAME, "alice2@example.com", PASSWORD).isPresent());
    }

    @Test
    @DisplayName("saveUser assigns distinct, increasing ids")
    void saveUser_assignsIncreasingIds() {
        User first = db.saveUser("a", "a@example.com", PASSWORD).orElseThrow();
        User second = db.saveUser("b", "b@example.com", PASSWORD).orElseThrow();

        assertTrue(second.id() > first.id());
    }

    @Test
    @DisplayName(
            "saveUser fails with IllegalStateException on a non-constraint DB error (null email)")
    void saveUser_nullEmail_throws() {
        assertThrows(IllegalStateException.class, () -> db.saveUser(USERNAME, null, PASSWORD));
    }

    /* ---------- login ---------- */

    @Test
    @DisplayName("login succeeds with correct credentials")
    void login_validCredentials_returnsUser() {
        User saved = db.saveUser(USERNAME, EMAIL, PASSWORD).orElseThrow();

        Optional<User> result = db.login(EMAIL, PASSWORD);

        assertTrue(result.isPresent());
        assertEquals(saved.id(), result.get().id());
        assertEquals(USERNAME, result.get().username());
        assertEquals(EMAIL, result.get().email());
    }

    @Test
    @DisplayName("login ignores email case and surrounding whitespace")
    void login_emailIsNormalized() {
        db.saveUser(USERNAME, EMAIL, PASSWORD);

        assertTrue(db.login("  ALICE@Example.com ", PASSWORD).isPresent());
    }

    @Test
    @DisplayName("login fails with a wrong password")
    void login_wrongPassword_returnsEmpty() {
        db.saveUser(USERNAME, EMAIL, PASSWORD);

        assertTrue(db.login(EMAIL, "wrong-password").isEmpty());
    }

    @Test
    @DisplayName("login treats the password as case-sensitive")
    void login_passwordCaseSensitive() {
        db.saveUser(USERNAME, EMAIL, PASSWORD);

        assertTrue(db.login(EMAIL, PASSWORD.toUpperCase()).isEmpty());
    }

    @Test
    @DisplayName("login fails for an unknown email")
    void login_unknownEmail_returnsEmpty() {
        db.saveUser(USERNAME, EMAIL, PASSWORD);

        assertTrue(db.login("nobody@example.com", PASSWORD).isEmpty());
    }

    @Test
    @DisplayName("login fails when the password is null")
    void login_nullPassword_returnsEmpty() {
        db.saveUser(USERNAME, EMAIL, PASSWORD);

        assertTrue(db.login(EMAIL, null).isEmpty());
    }

    @Test
    @DisplayName("login fails when the email is null")
    void login_nullEmail_returnsEmpty() {
        db.saveUser(USERNAME, EMAIL, PASSWORD);

        assertTrue(db.login(null, PASSWORD).isEmpty());
    }

    @Test
    @DisplayName("login on an empty database returns empty")
    void login_emptyDatabase_returnsEmpty() {
        assertTrue(db.login(EMAIL, PASSWORD).isEmpty());
    }

    @Test
    @DisplayName("login picks the right user when several exist")
    void login_multipleUsers_returnsMatchingUser() {
        db.saveUser("alice", "alice@example.com", "alicePass1");
        User bob = db.saveUser("bob", "bob@example.com", "bobPass22").orElseThrow();

        Optional<User> result = db.login("bob@example.com", "bobPass22");

        assertTrue(result.isPresent());
        assertEquals(bob.id(), result.get().id());
        assertTrue(db.login("bob@example.com", "alicePass1").isEmpty());
    }

    /* ---------- persistence ---------- */

    @Test
    @DisplayName("data persists across repository instances on the same file")
    void data_persistsAcrossInstances() {
        db.saveUser(USERNAME, EMAIL, PASSWORD);

        SQLiteUserDatabase reopened = new SQLiteUserDatabase(dbFile.toString());

        assertTrue(reopened.login(EMAIL, PASSWORD).isPresent());
        assertTrue(reopened.saveUser("other", EMAIL, PASSWORD).isEmpty());
    }

    /* ---------- deleteUser ---------- */

    @Test
    void deleteUser_withExistingId_returnsTrueAndRemovesUser() {
        User saved = db.saveUser(USERNAME, EMAIL, PASSWORD).orElseThrow();

        assertTrue(db.deleteUser(saved.id()));
        assertTrue(db.login(EMAIL, PASSWORD).isEmpty());
    }

    @Test
    void deleteUser_withUnknownId_returnsFalse() {
        assertFalse(db.deleteUser(9999L));
    }

    @Test
    void deleteUser_calledTwice_secondCallReturnsFalse() {
        User saved = db.saveUser(USERNAME, EMAIL, PASSWORD).orElseThrow();

        assertTrue(db.deleteUser(saved.id()));
        assertFalse(db.deleteUser(saved.id()));
    }

    @Test
    void deleteUser_onlyRemovesTheTargetUser() {
        User a = db.saveUser("a", "a@example.com", PASSWORD).orElseThrow();
        db.saveUser("b", "b@example.com", PASSWORD);

        db.deleteUser(a.id());

        assertTrue(db.login("a@example.com", PASSWORD).isEmpty());
        assertTrue(db.login("b@example.com", PASSWORD).isPresent());
    }

    @Test
    void deleteUser_freesEmailForReRegistration() {
        User saved = db.saveUser(USERNAME, EMAIL, PASSWORD).orElseThrow();
        db.deleteUser(saved.id());

        assertTrue(db.saveUser(USERNAME, EMAIL, PASSWORD).isPresent());
    }

    /* ---------- stats table ---------- */

    private Connection rawConnection() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        return DriverManager.getConnection(
                "jdbc:sqlite:" + tempDir.resolve("test-accounts.db"), config.toProperties());
    }

    @Test
    void statsTable_defaultsToZeroValues() throws SQLException {
        User saved = db.saveUser(USERNAME, EMAIL, PASSWORD).orElseThrow();

        try (Connection conn = rawConnection();
                Statement st = conn.createStatement()) {
            st.executeUpdate("INSERT INTO stats (user_id) VALUES (" + saved.id() + ")");
            try (ResultSet rs = st.executeQuery("SELECT quizzes_completed, accuracy FROM stats")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt("quizzes_completed"));
                assertEquals(0.0, rs.getDouble("accuracy"));
            }
        }
    }

    @Test
    void statsTable_rejectsUnknownUserId() throws SQLException {
        try (Connection conn = rawConnection();
                Statement st = conn.createStatement()) {
            assertThrows(
                    SQLException.class,
                    () -> st.executeUpdate("INSERT INTO stats (user_id) VALUES (9999)"));
        }
    }

    @Test
    void deleteUser_alsoRemovesTheirStats() throws SQLException {
        User saved = db.saveUser(USERNAME, EMAIL, PASSWORD).orElseThrow();
        try (Connection conn = rawConnection();
                Statement st = conn.createStatement()) {
            st.executeUpdate(
                    "INSERT INTO stats (user_id, quizzes_completed, accuracy) VALUES ("
                            + saved.id()
                            + ", 5, 0.8)");

            assertTrue(db.deleteUser(saved.id()));

            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM stats")) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1));
            }
        }
    }
}
