package org.ssd.project.accounts;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for storing and fetching users. Password hashing and email normalization belong to {@link
 * AuthService} and are tested in {@link AuthServiceTest}; stats are tested in {@link
 * SQLiteStatsRepositoryTest}.
 */
class SQLiteUserRepositoryTest {

    private static final String USERNAME = "alice";
    private static final String EMAIL = "alice@example.com";
    private static final String HASH = "$2a$12$not-a-real-hash-just-stored-as-is";
    private static final String NEW_HASH = "$2a$12$another-hash-also-stored-as-is";

    @TempDir Path tempDir;

    private SQLiteDatabase database;
    private IUserRepository users;

    @BeforeEach
    void setUp() {
        database = new SQLiteDatabase(tempDir.resolve("test-users.db").toString());
        users = new SQLiteUserRepository(database);
    }

    /* ---------- helpers ---------- */

    private int count(String table) throws SQLException {
        try (Connection conn = database.connect();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private User createAlice() {
        return users.create(USERNAME, EMAIL, HASH).orElseThrow();
    }

    /* ---------- create ---------- */

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("stores a new user and returns it exactly as given")
        void newUser_returnsUser() {
            Optional<User> saved = users.create(USERNAME, EMAIL, HASH);

            assertTrue(saved.isPresent());
            assertAll(
                    () -> assertEquals(USERNAME, saved.get().username()),
                    () -> assertEquals(EMAIL, saved.get().email()),
                    () -> assertEquals(HASH, saved.get().passwordHash()),
                    () -> assertTrue(saved.get().id() > 0));
        }

        @Test
        @DisplayName("stores the hash untouched")
        void hashIsStoredVerbatim() {
            User saved = createAlice();

            assertEquals(HASH, users.findByEmail(EMAIL).orElseThrow().passwordHash());
            assertEquals(HASH, saved.passwordHash());
        }

        @Test
        @DisplayName("assigns distinct, increasing ids")
        void increasingIds() {
            User first = users.create("a", "a@example.com", HASH).orElseThrow();
            User second = users.create("b", "b@example.com", HASH).orElseThrow();

            assertTrue(second.id() > first.id());
        }

        @Test
        @DisplayName("allows the same username with different emails")
        void sameUsernameDifferentEmail_succeeds() {
            createAlice();

            assertTrue(users.create(USERNAME, "alice2@example.com", HASH).isPresent());
        }

        @Test
        @DisplayName("rejects a duplicate email")
        void duplicateEmail_returnsEmpty() throws SQLException {
            createAlice();

            assertTrue(users.create("bob", EMAIL, HASH).isEmpty());
            assertEquals(1, count("users"));
        }

        @Test
        @DisplayName("treats emails that differ only by case as duplicates")
        void duplicateEmailDifferentCase_returnsEmpty() {
            createAlice();

            assertTrue(users.create("bob", "ALICE@EXAMPLE.COM", HASH).isEmpty());
        }

        @Test
        @DisplayName("a null column value is a database error, not a duplicate")
        void nullValues_throw() throws SQLException {
            assertAll(
                    () ->
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> users.create(null, EMAIL, HASH)),
                    () ->
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> users.create(USERNAME, null, HASH)),
                    () ->
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> users.create(USERNAME, EMAIL, null)));

            assertEquals(0, count("users"));
        }

        @Test
        @DisplayName("treats SQL metacharacters in the input as plain data")
        void sqlMetacharacters_areStoredVerbatim() throws SQLException {
            String nastyName = "Robert'); DROP TABLE users;--";
            String nastyEmail = "x'; DROP TABLE users; --@example.com";

            User saved = users.create(nastyName, nastyEmail, HASH).orElseThrow();

            assertAll(
                    () -> assertEquals(nastyName, saved.username()),
                    () -> assertEquals(1, count("users")),
                    () -> assertTrue(users.findByEmail(nastyEmail).isPresent()));
        }
    }

    /* ---------- findByEmail ---------- */

    @Nested
    @DisplayName("findByEmail")
    class FindByEmail {

        @Test
        @DisplayName("returns the stored user with all fields")
        void existingEmail_returnsUser() {
            User saved = createAlice();

            Optional<User> found = users.findByEmail(EMAIL);

            assertTrue(found.isPresent());
            assertAll(
                    () -> assertEquals(saved.id(), found.get().id()),
                    () -> assertEquals(USERNAME, found.get().username()),
                    () -> assertEquals(EMAIL, found.get().email()),
                    () -> assertEquals(HASH, found.get().passwordHash()));
        }

        @Test
        @DisplayName("matches the email regardless of case")
        void lookupIsCaseInsensitive() {
            createAlice();

            assertTrue(users.findByEmail("ALICE@Example.com").isPresent());
        }

        @Test
        @DisplayName("returns empty for an unknown email")
        void unknownEmail_returnsEmpty() {
            createAlice();

            assertTrue(users.findByEmail("nobody@example.com").isEmpty());
        }

        @Test
        @DisplayName("returns empty for a null email")
        void nullEmail_returnsEmpty() {
            createAlice();

            assertTrue(users.findByEmail(null).isEmpty());
        }

        @Test
        @DisplayName("returns empty on an empty database")
        void emptyDatabase_returnsEmpty() {
            assertTrue(users.findByEmail(EMAIL).isEmpty());
        }

        @Test
        @DisplayName("picks the right user when several exist")
        void multipleUsers_returnsMatchingUser() {
            users.create("alice", "alice@example.com", "hashA");
            User bob = users.create("bob", "bob@example.com", "hashB").orElseThrow();

            Optional<User> found = users.findByEmail("bob@example.com");

            assertTrue(found.isPresent());
            assertAll(
                    () -> assertEquals(bob.id(), found.get().id()),
                    () -> assertEquals("hashB", found.get().passwordHash()));
        }

        @Test
        @DisplayName("is not fooled by SQL injection in the email")
        void sqlInjectionInEmail_returnsEmpty() {
            createAlice();

            assertAll(
                    () -> assertTrue(users.findByEmail("' OR '1'='1").isEmpty()),
                    () -> assertTrue(users.findByEmail("nobody@example.com' OR '1'='1").isEmpty()));
        }
    }

    /* ---------- findById ---------- */

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("returns the stored user with all fields")
        void existingId_returnsUser() {
            User saved = createAlice();

            Optional<User> found = users.findById(saved.id());

            assertTrue(found.isPresent());
            assertAll(
                    () -> assertEquals(saved.id(), found.get().id()),
                    () -> assertEquals(USERNAME, found.get().username()),
                    () -> assertEquals(EMAIL, found.get().email()),
                    () -> assertEquals(HASH, found.get().passwordHash()));
        }

        @Test
        @DisplayName("returns empty for an unknown or invalid id")
        void unknownId_returnsEmpty() {
            createAlice();

            assertAll(
                    () -> assertTrue(users.findById(9999L).isEmpty()),
                    () -> assertTrue(users.findById(0L).isEmpty()),
                    () -> assertTrue(users.findById(-1L).isEmpty()));
        }

        @Test
        @DisplayName("returns empty on an empty database")
        void emptyDatabase_returnsEmpty() {
            assertTrue(users.findById(1L).isEmpty());
        }

        @Test
        @DisplayName("picks the right user when several exist")
        void multipleUsers_returnsMatchingUser() {
            users.create("alice", "alice@example.com", "hashA");
            User bob = users.create("bob", "bob@example.com", "hashB").orElseThrow();

            Optional<User> found = users.findById(bob.id());

            assertTrue(found.isPresent());
            assertAll(
                    () -> assertEquals("bob", found.get().username()),
                    () -> assertEquals("hashB", found.get().passwordHash()));
        }

        @Test
        @DisplayName("returns empty once the user has been deleted")
        void deletedUser_returnsEmpty() {
            User saved = createAlice();
            users.deleteById(saved.id());

            assertTrue(users.findById(saved.id()).isEmpty());
        }
    }

    /* ---------- updateUsername ---------- */

    @Nested
    @DisplayName("updateUsername")
    class UpdateUsername {

        @Test
        @DisplayName("returns the updated user and leaves the other fields alone")
        void existingId_returnsUpdatedUser() {
            User saved = createAlice();

            Optional<User> updated = users.updateUsername(saved.id(), "alice2");

            assertTrue(updated.isPresent());
            assertAll(
                    () -> assertEquals(saved.id(), updated.get().id()),
                    () -> assertEquals("alice2", updated.get().username()),
                    () -> assertEquals(EMAIL, updated.get().email()),
                    () -> assertEquals(HASH, updated.get().passwordHash()));
        }

        @Test
        @DisplayName("persists the new username")
        void persistsChange() {
            User saved = createAlice();

            users.updateUsername(saved.id(), "alice2");

            assertAll(
                    () ->
                            assertEquals(
                                    "alice2", users.findById(saved.id()).orElseThrow().username()),
                    () ->
                            assertEquals(
                                    "alice2", users.findByEmail(EMAIL).orElseThrow().username()));
        }

        @Test
        @DisplayName("only changes the target user")
        void onlyChangesTheTargetUser() {
            User a = users.create("a", "a@example.com", HASH).orElseThrow();
            User b = users.create("b", "b@example.com", HASH).orElseThrow();

            users.updateUsername(a.id(), "a-renamed");

            assertAll(
                    () ->
                            assertEquals(
                                    "a-renamed", users.findById(a.id()).orElseThrow().username()),
                    () -> assertEquals("b", users.findById(b.id()).orElseThrow().username()));
        }

        @Test
        @DisplayName("returns empty for an unknown or invalid id and changes nothing")
        void unknownId_returnsEmpty() throws SQLException {
            User saved = createAlice();

            assertAll(
                    () -> assertTrue(users.updateUsername(9999L, "x").isEmpty()),
                    () -> assertTrue(users.updateUsername(0L, "x").isEmpty()),
                    () -> assertTrue(users.updateUsername(-1L, "x").isEmpty()));

            assertAll(
                    () -> assertEquals(1, count("users")),
                    () ->
                            assertEquals(
                                    USERNAME, users.findById(saved.id()).orElseThrow().username()));
        }

        @Test
        @DisplayName("allows a username that another user already has")
        void duplicateUsername_isAllowed() {
            users.create("a", "a@example.com", HASH);
            User b = users.create("b", "b@example.com", HASH).orElseThrow();

            Optional<User> updated = users.updateUsername(b.id(), "a");

            assertTrue(updated.isPresent());
            assertEquals("a", updated.get().username());
        }

        @Test
        @DisplayName("a null username is a database error and keeps the old one")
        void nullUsername_throws() {
            User saved = createAlice();

            assertThrows(IllegalStateException.class, () -> users.updateUsername(saved.id(), null));

            assertEquals(USERNAME, users.findById(saved.id()).orElseThrow().username());
        }

        @Test
        @DisplayName("treats SQL metacharacters in the username as plain data")
        void sqlMetacharacters_areStoredVerbatim() throws SQLException {
            User saved = createAlice();
            String nastyName = "Robert'); DROP TABLE users;--";

            Optional<User> updated = users.updateUsername(saved.id(), nastyName);

            assertAll(
                    () -> assertEquals(nastyName, updated.orElseThrow().username()),
                    () -> assertEquals(1, count("users")));
        }
    }

    /* ---------- updatePasswordHash ---------- */

    @Nested
    @DisplayName("updatePasswordHash")
    class UpdatePasswordHash {

        @Test
        @DisplayName("returns the updated user and leaves the other fields alone")
        void existingId_returnsUpdatedUser() {
            User saved = createAlice();

            Optional<User> updated = users.updatePasswordHash(saved.id(), NEW_HASH);

            assertTrue(updated.isPresent());
            assertAll(
                    () -> assertEquals(saved.id(), updated.get().id()),
                    () -> assertEquals(USERNAME, updated.get().username()),
                    () -> assertEquals(EMAIL, updated.get().email()),
                    () -> assertEquals(NEW_HASH, updated.get().passwordHash()));
        }

        @Test
        @DisplayName("persists the new hash untouched")
        void persistsChange() {
            User saved = createAlice();

            users.updatePasswordHash(saved.id(), NEW_HASH);

            assertAll(
                    () ->
                            assertEquals(
                                    NEW_HASH,
                                    users.findById(saved.id()).orElseThrow().passwordHash()),
                    () ->
                            assertEquals(
                                    NEW_HASH,
                                    users.findByEmail(EMAIL).orElseThrow().passwordHash()));
        }

        @Test
        @DisplayName("only changes the target user")
        void onlyChangesTheTargetUser() {
            User a = users.create("a", "a@example.com", "hashA").orElseThrow();
            User b = users.create("b", "b@example.com", "hashB").orElseThrow();

            users.updatePasswordHash(a.id(), NEW_HASH);

            assertAll(
                    () ->
                            assertEquals(
                                    NEW_HASH, users.findById(a.id()).orElseThrow().passwordHash()),
                    () ->
                            assertEquals(
                                    "hashB", users.findById(b.id()).orElseThrow().passwordHash()));
        }

        @Test
        @DisplayName("returns empty for an unknown or invalid id and changes nothing")
        void unknownId_returnsEmpty() throws SQLException {
            User saved = createAlice();

            assertAll(
                    () -> assertTrue(users.updatePasswordHash(9999L, NEW_HASH).isEmpty()),
                    () -> assertTrue(users.updatePasswordHash(0L, NEW_HASH).isEmpty()),
                    () -> assertTrue(users.updatePasswordHash(-1L, NEW_HASH).isEmpty()));

            assertAll(
                    () -> assertEquals(1, count("users")),
                    () ->
                            assertEquals(
                                    HASH, users.findById(saved.id()).orElseThrow().passwordHash()));
        }

        @Test
        @DisplayName("a null hash is a database error and keeps the old one")
        void nullHash_throws() {
            User saved = createAlice();

            assertThrows(
                    IllegalStateException.class, () -> users.updatePasswordHash(saved.id(), null));

            assertEquals(HASH, users.findById(saved.id()).orElseThrow().passwordHash());
        }

        @Test
        @DisplayName("does not touch the stats row")
        void statsRowIsUntouched() throws SQLException {
            User saved = createAlice();

            users.updatePasswordHash(saved.id(), NEW_HASH);

            assertEquals(1, count("stats"));
        }
    }

    /* ---------- deleteById ---------- */

    @Nested
    @DisplayName("deleteById")
    class DeleteById {

        @Test
        @DisplayName("returns true and removes the user")
        void existingId_returnsTrueAndRemovesUser() throws SQLException {
            User saved = createAlice();

            assertTrue(users.deleteById(saved.id()));

            assertAll(
                    () -> assertTrue(users.findByEmail(EMAIL).isEmpty()),
                    () -> assertEquals(0, count("users")));
        }

        @Test
        @DisplayName("returns false for an unknown or invalid id")
        void unknownId_returnsFalse() {
            assertAll(
                    () -> assertFalse(users.deleteById(9999L)),
                    () -> assertFalse(users.deleteById(0L)),
                    () -> assertFalse(users.deleteById(-1L)));
        }

        @Test
        @DisplayName("returns false the second time for the same id")
        void calledTwice_secondCallReturnsFalse() {
            User saved = createAlice();

            assertTrue(users.deleteById(saved.id()));
            assertFalse(users.deleteById(saved.id()));
        }

        @Test
        @DisplayName("only removes the target user")
        void onlyRemovesTheTargetUser() {
            User a = users.create("a", "a@example.com", HASH).orElseThrow();
            users.create("b", "b@example.com", HASH);

            users.deleteById(a.id());

            assertAll(
                    () -> assertTrue(users.findByEmail("a@example.com").isEmpty()),
                    () -> assertTrue(users.findByEmail("b@example.com").isPresent()));
        }

        @Test
        @DisplayName("frees the email for re-registration")
        void freesEmailForReRegistration() {
            User saved = createAlice();
            users.deleteById(saved.id());

            assertTrue(users.create(USERNAME, EMAIL, HASH).isPresent());
        }
    }
}
