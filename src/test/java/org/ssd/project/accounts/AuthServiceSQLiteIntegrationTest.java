package org.ssd.project.accounts;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ssd.project.email.IEmailService;

/** Wiring test: {@link AuthService} on top of the real SQLite repositories and real BCrypt. */
class AuthServiceSQLiteIntegrationTest {

    private static final String USERNAME = "alice";
    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "S3cret!pass";
    private static final String NEW_USERNAME = "alice-renamed";
    private static final String NEW_PASSWORD = "N3w!passw0rd";

    @TempDir Path tempDir;

    private IUserRepository users;
    private IStatsRepository stats;
    private Mailbox mailbox;
    private AuthService auth;

    /** Captures verification codes sent during registration. */
    private static final class Mailbox implements IEmailService {
        final List<String> codes = new ArrayList<>();

        @Override
        public void sendWelcomeEmail(User user) {}

        @Override
        public void sendVerificationEmail(String username, String email, String code) {
            codes.add(code);
        }

        String last() {
            return codes.get(codes.size() - 1);
        }
    }

    @BeforeEach
    void setUp() {
        SQLiteDatabase database = new SQLiteDatabase(tempDir.resolve("auth-test.db").toString());
        users = new SQLiteUserRepository(database);
        stats = new SQLiteStatsRepository(database);
        mailbox = new Mailbox();
        auth = new AuthService(users, new BCryptPasswordService(), mailbox, Clock.systemUTC());
    }

    /** Helper: runs the full start→complete registration flow and returns the created user. */
    private User register(String username, String email, String password) {
        auth.startRegistration(username, email, password);
        return auth.completeRegistration(email, mailbox.last()).user();
    }

    @Test
    @DisplayName("register stores a BCrypt hash, never the raw password")
    void register_storesBCryptHash() {
        User saved = register(USERNAME, EMAIL, PASSWORD);

        User stored = users.findByEmail(EMAIL).orElseThrow();
        assertAll(
                () -> assertNotEquals(PASSWORD, saved.passwordHash()),
                () -> assertTrue(saved.passwordHash().startsWith("$2")),
                () -> assertEquals(saved.passwordHash(), stored.passwordHash()));
    }

    @Test
    @DisplayName("a registered user can log in, whatever the case of the email")
    void register_thenLogin() {
        User saved = register(USERNAME, "  Alice@Example.COM ", PASSWORD);

        assertAll(
                () -> assertEquals(EMAIL, saved.email()),
                () -> assertEquals(saved.id(), auth.login(EMAIL, PASSWORD).orElseThrow().id()),
                () -> assertTrue(auth.login("ALICE@EXAMPLE.COM", PASSWORD).isPresent()),
                () -> assertTrue(auth.login(EMAIL, "wrong-password").isEmpty()));
    }

    @Test
    @DisplayName("registering the same email twice is rejected")
    void duplicateRegistration_returnsEmpty() {
        register(USERNAME, EMAIL, PASSWORD);

        assertEquals(
                AuthService.StartResult.EMAIL_TAKEN,
                auth.startRegistration("bob", "  ALICE@EXAMPLE.COM ", "otherPass1"));
    }

    @Test
    @DisplayName("a registered user starts with default stats")
    void register_createsDefaultStats() {
        User saved = register(USERNAME, EMAIL, PASSWORD);

        assertAll(
                () -> assertEquals(0, stats.getQuizzesCompleted(saved.id())),
                () -> assertEquals(0.0, stats.getAccuracy(saved.id()), 1e-9));
    }

    @Test
    @DisplayName("a deleted user can no longer log in")
    void deletedUser_cannotLogIn() {
        User saved = register(USERNAME, EMAIL, PASSWORD);

        assertTrue(users.deleteById(saved.id()));

        assertTrue(auth.login(EMAIL, PASSWORD).isEmpty());
    }

    @Test
    @DisplayName("login is not fooled by SQL injection in the email")
    void sqlInjectionInEmail_returnsEmpty() {
        register(USERNAME, EMAIL, PASSWORD);

        assertAll(
                () -> assertTrue(auth.login("' OR '1'='1", PASSWORD).isEmpty()),
                () -> assertTrue(auth.login("nobody@example.com' OR '1'='1", PASSWORD).isEmpty()));
    }

    /* ---------- changeUsername / changePassword ---------- */

    @Test
    @DisplayName("a changed username is stored and returned on the next login")
    void changeUsername_isPersisted() {
        User saved = register(USERNAME, EMAIL, PASSWORD);

        User updated = auth.changeUsername(saved.id(), NEW_USERNAME).orElseThrow();

        assertAll(
                () -> assertEquals(NEW_USERNAME, updated.username()),
                () ->
                        assertEquals(
                                NEW_USERNAME, users.findById(saved.id()).orElseThrow().username()),
                () ->
                        assertEquals(
                                NEW_USERNAME,
                                auth.login(EMAIL, PASSWORD).orElseThrow().username()));
    }

    @Test
    @DisplayName("a changed password is a new BCrypt hash; only the new password logs in")
    void changePassword_replacesHash() {
        User saved = register(USERNAME, EMAIL, PASSWORD);

        User updated = auth.changePassword(saved.id(), PASSWORD, NEW_PASSWORD).orElseThrow();

        assertAll(
                () -> assertTrue(updated.passwordHash().startsWith("$2")),
                () -> assertNotEquals(saved.passwordHash(), updated.passwordHash()),
                () -> assertNotEquals(NEW_PASSWORD, updated.passwordHash()),
                () -> assertTrue(auth.login(EMAIL, NEW_PASSWORD).isPresent()),
                () -> assertTrue(auth.login(EMAIL, PASSWORD).isEmpty()));
    }

    @Test
    @DisplayName("a wrong current password leaves the stored hash and the old login untouched")
    void changePassword_wrongCurrentPassword_changesNothing() {
        User saved = register(USERNAME, EMAIL, PASSWORD);

        assertTrue(auth.changePassword(saved.id(), "wrong-password", NEW_PASSWORD).isEmpty());

        assertAll(
                () ->
                        assertEquals(
                                saved.passwordHash(),
                                users.findById(saved.id()).orElseThrow().passwordHash()),
                () -> assertTrue(auth.login(EMAIL, PASSWORD).isPresent()),
                () -> assertTrue(auth.login(EMAIL, NEW_PASSWORD).isEmpty()));
    }

    @Test
    @DisplayName("changing username and password keeps the user's stats")
    void changes_keepStats() {
        User saved = register(USERNAME, EMAIL, PASSWORD);
        stats.setQuizzesCompleted(saved.id(), 5);
        stats.setAccuracy(saved.id(), 0.8);

        auth.changeUsername(saved.id(), NEW_USERNAME);
        auth.changePassword(saved.id(), PASSWORD, NEW_PASSWORD);

        assertAll(
                () -> assertEquals(5, stats.getQuizzesCompleted(saved.id())),
                () -> assertEquals(0.8, stats.getAccuracy(saved.id()), 1e-9));
    }

    @Test
    @DisplayName("a deleted user cannot be changed")
    void deletedUser_cannotBeChanged() {
        User saved = register(USERNAME, EMAIL, PASSWORD);
        users.deleteById(saved.id());

        assertAll(
                () -> assertTrue(auth.changeUsername(saved.id(), NEW_USERNAME).isEmpty()),
                () ->
                        assertTrue(
                                auth.changePassword(saved.id(), PASSWORD, NEW_PASSWORD).isEmpty()));
    }
}
