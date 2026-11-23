package org.ssd.project.accounts;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.ssd.project.email.IEmailService;

/**
 * Unit tests for {@link AuthService} using an in-memory repository and a trivial password service,
 * so no database or BCrypt work is involved. The wiring with SQLite and real BCrypt is covered by
 * {@link AuthServiceSQLiteIntegrationTest}.
 */
class AuthServiceTest {

    private static final String USERNAME = "alice";
    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "S3cret!pass";
    private static final String NEW_USERNAME = "alice-renamed";
    private static final String NEW_PASSWORD = "N3w!passw0rd";

    private InMemoryUserRepository users;
    private Mailbox mailbox;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        mailbox = new Mailbox();
        auth = new AuthService(users, new FakePasswordService(), mailbox, Clock.systemUTC());
    }

    /* ---------- test doubles ---------- */

    /** Captures verification codes so tests can complete the two-step registration flow. */
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

    /** Reversible on purpose, so tests can tell a hash from the raw password. */
    private static final class FakePasswordService implements IPasswordService {
        @Override
        public String hash(String rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean verify(String rawPassword, String hash) {
            return hash != null && hash.equals("hashed:" + rawPassword);
        }
    }

    /** Case-insensitive on email, like the NOCASE column in the real schema. */
    private static final class InMemoryUserRepository implements IUserRepository {
        private final Map<String, User> byEmail = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        private long nextId = 1;

        @Override
        public Optional<User> create(String username, String email, String passwordHash) {
            if (byEmail.containsKey(email)) {
                return Optional.empty();
            }
            User user = new User(nextId++, username, email, passwordHash);
            byEmail.put(email, user);
            return Optional.of(user);
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return email == null ? Optional.empty() : Optional.ofNullable(byEmail.get(email));
        }

        @Override
        public Optional<User> findById(long id) {
            return byEmail.values().stream().filter(user -> user.id() == id).findFirst();
        }

        @Override
        public Optional<User> updateUsername(long id, String username) {
            return replace(
                    id, old -> new User(old.id(), username, old.email(), old.passwordHash()));
        }

        @Override
        public Optional<User> updatePasswordHash(long id, String passwordHash) {
            return replace(
                    id, old -> new User(old.id(), old.username(), old.email(), passwordHash));
        }

        @Override
        public boolean deleteById(long id) {
            return byEmail.values().removeIf(user -> user.id() == id);
        }

        private Optional<User> replace(long id, UnaryOperator<User> change) {
            return findById(id)
                    .map(
                            old -> {
                                User updated = change.apply(old);
                                byEmail.put(old.email(), updated);
                                return updated;
                            });
        }

        int size() {
            return byEmail.size();
        }
    }

    private User registerAlice() {
        return register(USERNAME, EMAIL, PASSWORD);
    }

    /** Helper: runs the full start→complete registration flow and returns the created user. */
    private User register(String username, String email, String password) {
        auth.startRegistration(username, email, password);
        return auth.completeRegistration(email, mailbox.last()).user();
    }

    /* ---------- register ---------- */

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("stores a new user and returns it")
        void newUser_returnsUser() {
            User saved = register(USERNAME, EMAIL, PASSWORD);

            assertAll(
                    () -> assertEquals(USERNAME, saved.username()),
                    () -> assertEquals(EMAIL, saved.email()),
                    () -> assertTrue(saved.id() > 0),
                    () -> assertEquals(1, users.size()));
        }

        @Test
        @DisplayName("passes a hash to the repository, never the raw password")
        void passwordIsHashed() {
            User saved = registerAlice();

            assertAll(
                    () -> assertNotEquals(PASSWORD, saved.passwordHash()),
                    () -> assertEquals("hashed:" + PASSWORD, saved.passwordHash()));
        }

        @Test
        @DisplayName("trims and lowercases the email before storing")
        void normalizesEmail() {
            User saved = register(USERNAME, "  Alice@Example.COM  ", PASSWORD);

            assertEquals("alice@example.com", saved.email());
        }

        @Test
        @DisplayName("allows the same username with different emails")
        void sameUsernameDifferentEmail_succeeds() {
            registerAlice();

            assertTrue(register(USERNAME, "alice2@example.com", PASSWORD) != null);
        }

        @Test
        @DisplayName("rejects a duplicate email")
        void duplicateEmail_returnsEmpty() {
            registerAlice();

            assertEquals(
                    AuthService.StartResult.EMAIL_TAKEN,
                    auth.startRegistration("bob", EMAIL, "otherPass1"));
            assertEquals(1, users.size());
        }

        @Test
        @DisplayName("treats emails as duplicates regardless of case and whitespace")
        void duplicateEmailDifferentCase_returnsEmpty() {
            registerAlice();

            assertEquals(
                    AuthService.StartResult.EMAIL_TAKEN,
                    auth.startRegistration("bob", "  ALICE@EXAMPLE.COM ", "otherPass1"));
        }

        @Test
        @DisplayName("rejects a null or blank username")
        void invalidUsername_throws() {
            assertAll(
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> auth.startRegistration(null, EMAIL, PASSWORD)),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> auth.startRegistration("   ", EMAIL, PASSWORD)));

            assertEquals(0, users.size());
        }

        @Test
        @DisplayName("rejects a null or blank email")
        void invalidEmail_throws() {
            assertAll(
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> auth.startRegistration(USERNAME, null, PASSWORD)),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> auth.startRegistration(USERNAME, "  ", PASSWORD)));

            assertEquals(0, users.size());
        }

        @Test
        @DisplayName("rejects a null or blank password")
        void invalidPassword_throws() {
            assertAll(
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> auth.startRegistration(USERNAME, EMAIL, null)),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> auth.startRegistration(USERNAME, EMAIL, "   ")));

            assertEquals(0, users.size());
        }
    }

    /* ---------- login ---------- */

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("succeeds with correct credentials")
        void validCredentials_returnsUser() {
            User saved = registerAlice();

            Optional<User> result = auth.login(EMAIL, PASSWORD);

            assertTrue(result.isPresent());
            assertAll(
                    () -> assertEquals(saved.id(), result.get().id()),
                    () -> assertEquals(USERNAME, result.get().username()),
                    () -> assertEquals(EMAIL, result.get().email()));
        }

        @Test
        @DisplayName("ignores email case and surrounding whitespace")
        void emailIsNormalized() {
            registerAlice();

            assertTrue(auth.login("  ALICE@Example.com ", PASSWORD).isPresent());
        }

        @Test
        @DisplayName("fails with a wrong password")
        void wrongPassword_returnsEmpty() {
            registerAlice();

            assertTrue(auth.login(EMAIL, "wrong-password").isEmpty());
        }

        @Test
        @DisplayName("treats the password as case-sensitive")
        void passwordIsCaseSensitive() {
            registerAlice();

            assertTrue(auth.login(EMAIL, PASSWORD.toUpperCase()).isEmpty());
        }

        @Test
        @DisplayName("fails for an unknown email")
        void unknownEmail_returnsEmpty() {
            registerAlice();

            assertTrue(auth.login("nobody@example.com", PASSWORD).isEmpty());
        }

        @Test
        @DisplayName("fails when the password is null")
        void nullPassword_returnsEmpty() {
            registerAlice();

            assertTrue(auth.login(EMAIL, null).isEmpty());
        }

        @Test
        @DisplayName("fails when the email is null")
        void nullEmail_returnsEmpty() {
            registerAlice();

            assertTrue(auth.login(null, PASSWORD).isEmpty());
        }

        @Test
        @DisplayName("returns empty when no users exist")
        void emptyRepository_returnsEmpty() {
            assertTrue(auth.login(EMAIL, PASSWORD).isEmpty());
        }

        @Test
        @DisplayName("picks the right user when several exist")
        void multipleUsers_returnsMatchingUser() {
            register("alice", "alice@example.com", "alicePass1");
            User bob = register("bob", "bob@example.com", "bobPass22");

            Optional<User> result = auth.login("bob@example.com", "bobPass22");

            assertTrue(result.isPresent());
            assertEquals(bob.id(), result.get().id());
            assertTrue(auth.login("bob@example.com", "alicePass1").isEmpty());
        }
    }

    /* ---------- changeUsername ---------- */

    @Nested
    @DisplayName("changeUsername")
    class ChangeUsername {

        @Test
        @DisplayName("returns the updated user and leaves the other fields alone")
        void existingUser_returnsUpdatedUser() {
            User saved = registerAlice();

            Optional<User> updated = auth.changeUsername(saved.id(), NEW_USERNAME);

            assertTrue(updated.isPresent());
            assertAll(
                    () -> assertEquals(saved.id(), updated.get().id()),
                    () -> assertEquals(NEW_USERNAME, updated.get().username()),
                    () -> assertEquals(EMAIL, updated.get().email()),
                    () -> assertEquals(saved.passwordHash(), updated.get().passwordHash()));
        }

        @Test
        @DisplayName("the new username shows up on the next login, and the password still works")
        void persistsAcrossLogin() {
            User saved = registerAlice();

            auth.changeUsername(saved.id(), NEW_USERNAME);

            assertEquals(NEW_USERNAME, auth.login(EMAIL, PASSWORD).orElseThrow().username());
        }

        @Test
        @DisplayName("does not require the password")
        void noPasswordNeeded() {
            User saved = registerAlice();

            assertTrue(auth.changeUsername(saved.id(), NEW_USERNAME).isPresent());
        }

        @Test
        @DisplayName("returns empty for an unknown user")
        void unknownUser_returnsEmpty() {
            registerAlice();

            assertTrue(auth.changeUsername(9999L, NEW_USERNAME).isEmpty());
        }

        @Test
        @DisplayName("rejects a null or blank username and keeps the old one")
        void invalidUsername_throws() {
            User saved = registerAlice();

            assertAll(
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> auth.changeUsername(saved.id(), null)),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> auth.changeUsername(saved.id(), "   ")));

            assertEquals(USERNAME, users.findById(saved.id()).orElseThrow().username());
        }

        @Test
        @DisplayName("only changes the target user")
        void onlyChangesTheTargetUser() {
            User alice = registerAlice();
            User bob = register("bob", "bob@example.com", "bobPass22");

            auth.changeUsername(alice.id(), NEW_USERNAME);

            assertEquals("bob", users.findById(bob.id()).orElseThrow().username());
        }
    }

    /* ---------- changePassword ---------- */

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("returns the updated user and leaves the other fields alone")
        void correctCurrentPassword_returnsUpdatedUser() {
            User saved = registerAlice();

            Optional<User> updated = auth.changePassword(saved.id(), PASSWORD, NEW_PASSWORD);

            assertTrue(updated.isPresent());
            assertAll(
                    () -> assertEquals(saved.id(), updated.get().id()),
                    () -> assertEquals(USERNAME, updated.get().username()),
                    () -> assertEquals(EMAIL, updated.get().email()),
                    () -> assertNotEquals(saved.passwordHash(), updated.get().passwordHash()));
        }

        @Test
        @DisplayName("stores a hash of the new password, never the raw one")
        void newPasswordIsHashed() {
            User saved = registerAlice();

            User updated = auth.changePassword(saved.id(), PASSWORD, NEW_PASSWORD).orElseThrow();

            assertAll(
                    () -> assertNotEquals(NEW_PASSWORD, updated.passwordHash()),
                    () -> assertEquals("hashed:" + NEW_PASSWORD, updated.passwordHash()));
        }

        @Test
        @DisplayName("the new password logs in and the old one no longer does")
        void newPasswordWorks_oldPasswordDoesNot() {
            User saved = registerAlice();

            auth.changePassword(saved.id(), PASSWORD, NEW_PASSWORD);

            assertAll(
                    () -> assertTrue(auth.login(EMAIL, NEW_PASSWORD).isPresent()),
                    () -> assertTrue(auth.login(EMAIL, PASSWORD).isEmpty()));
        }

        @Test
        @DisplayName("a wrong current password returns empty and keeps the old password")
        void wrongCurrentPassword_returnsEmpty() {
            User saved = registerAlice();

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
        @DisplayName("a null current password returns empty and keeps the old password")
        void nullCurrentPassword_returnsEmpty() {
            User saved = registerAlice();

            assertTrue(auth.changePassword(saved.id(), null, NEW_PASSWORD).isEmpty());

            assertTrue(auth.login(EMAIL, PASSWORD).isPresent());
        }

        @Test
        @DisplayName("returns empty for an unknown user")
        void unknownUser_returnsEmpty() {
            registerAlice();

            assertTrue(auth.changePassword(9999L, PASSWORD, NEW_PASSWORD).isEmpty());
        }

        @Test
        @DisplayName("rejects a null or blank new password and keeps the old one")
        void invalidNewPassword_throws() {
            User saved = registerAlice();

            assertAll(
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> auth.changePassword(saved.id(), PASSWORD, null)),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> auth.changePassword(saved.id(), PASSWORD, "   ")));

            assertTrue(auth.login(EMAIL, PASSWORD).isPresent());
        }

        @Test
        @DisplayName("only changes the target user")
        void onlyChangesTheTargetUser() {
            User alice = registerAlice();
            register("bob", "bob@example.com", "bobPass22");

            auth.changePassword(alice.id(), PASSWORD, NEW_PASSWORD);

            assertTrue(auth.login("bob@example.com", "bobPass22").isPresent());
        }

        @Test
        @DisplayName("the password can be changed more than once")
        void changeTwice() {
            User saved = registerAlice();

            auth.changePassword(saved.id(), PASSWORD, NEW_PASSWORD);
            auth.changePassword(saved.id(), NEW_PASSWORD, "Th1rd!passw0rd");

            assertAll(
                    () -> assertTrue(auth.login(EMAIL, "Th1rd!passw0rd").isPresent()),
                    () -> assertTrue(auth.login(EMAIL, NEW_PASSWORD).isEmpty()),
                    () -> assertTrue(auth.login(EMAIL, PASSWORD).isEmpty()));
        }
    }
}
