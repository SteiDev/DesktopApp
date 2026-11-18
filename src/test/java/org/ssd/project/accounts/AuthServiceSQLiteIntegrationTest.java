package org.ssd.project.accounts;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Wiring test: {@link AuthService} on top of the real SQLite repositories and real BCrypt. */
class AuthServiceSQLiteIntegrationTest {

    private static final String USERNAME = "alice";
    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "S3cret!pass";

    @TempDir Path tempDir;

    private IUserRepository users;
    private IStatsRepository stats;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        SQLiteDatabase database = new SQLiteDatabase(tempDir.resolve("auth-test.db").toString());
        users = new SQLiteUserRepository(database);
        stats = new SQLiteStatsRepository(database);
        auth = new AuthService(users);
    }

    @Test
    @DisplayName("register stores a BCrypt hash, never the raw password")
    void register_storesBCryptHash() {
        User saved = auth.register(USERNAME, EMAIL, PASSWORD).orElseThrow();

        User stored = users.findByEmail(EMAIL).orElseThrow();
        assertAll(
                () -> assertNotEquals(PASSWORD, saved.passwordHash()),
                () -> assertTrue(saved.passwordHash().startsWith("$2")),
                () -> assertEquals(saved.passwordHash(), stored.passwordHash()));
    }

    @Test
    @DisplayName("a registered user can log in, whatever the case of the email")
    void register_thenLogin() {
        User saved = auth.register(USERNAME, "  Alice@Example.COM ", PASSWORD).orElseThrow();

        assertAll(
                () -> assertEquals(EMAIL, saved.email()),
                () -> assertEquals(saved.id(), auth.login(EMAIL, PASSWORD).orElseThrow().id()),
                () -> assertTrue(auth.login("ALICE@EXAMPLE.COM", PASSWORD).isPresent()),
                () -> assertTrue(auth.login(EMAIL, "wrong-password").isEmpty()));
    }

    @Test
    @DisplayName("registering the same email twice is rejected")
    void duplicateRegistration_returnsEmpty() {
        auth.register(USERNAME, EMAIL, PASSWORD).orElseThrow();

        assertTrue(auth.register("bob", "  ALICE@EXAMPLE.COM ", "otherPass1").isEmpty());
    }

    @Test
    @DisplayName("a registered user starts with default stats")
    void register_createsDefaultStats() {
        User saved = auth.register(USERNAME, EMAIL, PASSWORD).orElseThrow();

        assertAll(
                () -> assertEquals(0, stats.getQuizzesCompleted(saved.id())),
                () -> assertEquals(0.0, stats.getAccuracy(saved.id()), 1e-9));
    }

    @Test
    @DisplayName("a deleted user can no longer log in")
    void deletedUser_cannotLogIn() {
        User saved = auth.register(USERNAME, EMAIL, PASSWORD).orElseThrow();

        assertTrue(users.deleteById(saved.id()));

        assertTrue(auth.login(EMAIL, PASSWORD).isEmpty());
    }

    @Test
    @DisplayName("login is not fooled by SQL injection in the email")
    void sqlInjectionInEmail_returnsEmpty() {
        auth.register(USERNAME, EMAIL, PASSWORD).orElseThrow();

        assertAll(
                () -> assertTrue(auth.login("' OR '1'='1", PASSWORD).isEmpty()),
                () -> assertTrue(auth.login("nobody@example.com' OR '1'='1", PASSWORD).isEmpty()));
    }
}
