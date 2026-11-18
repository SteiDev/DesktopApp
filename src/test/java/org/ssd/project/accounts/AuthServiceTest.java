package org.ssd.project.accounts;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AuthService} using an in-memory repository and a trivial password service,
 * so no database or BCrypt work is involved. The wiring with SQLite and real BCrypt is covered by
 * {@link AuthServiceSQLiteIntegrationTest}.
 */
class AuthServiceTest {

    private static final String USERNAME = "alice";
    private static final String EMAIL = "alice@example.com";
    private static final String PASSWORD = "S3cret!pass";

    private InMemoryUserRepository users;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        users = new InMemoryUserRepository();
        auth = new AuthService(users, new FakePasswordService());
    }

    /* ---------- test doubles ---------- */

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
        public boolean deleteById(long id) {
            return byEmail.values().removeIf(user -> user.id() == id);
        }

        int size() {
            return byEmail.size();
        }
    }

    private User registerAlice() {
        return auth.register(USERNAME, EMAIL, PASSWORD).orElseThrow();
    }

    /* ---------- register ---------- */

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("stores a new user and returns it")
        void newUser_returnsUser() {
            Optional<User> saved = auth.register(USERNAME, EMAIL, PASSWORD);

            assertTrue(saved.isPresent());
            assertAll(
                    () -> assertEquals(USERNAME, saved.get().username()),
                    () -> assertEquals(EMAIL, saved.get().email()),
                    () -> assertTrue(saved.get().id() > 0),
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
            User saved = auth.register(USERNAME, "  Alice@Example.COM  ", PASSWORD).orElseThrow();

            assertEquals("alice@example.com", saved.email());
        }

        @Test
        @DisplayName("allows the same username with different emails")
        void sameUsernameDifferentEmail_succeeds() {
            registerAlice();

            assertTrue(auth.register(USERNAME, "alice2@example.com", PASSWORD).isPresent());
        }

        @Test
        @DisplayName("rejects a duplicate email")
        void duplicateEmail_returnsEmpty() {
            registerAlice();

            assertTrue(auth.register("bob", EMAIL, "otherPass1").isEmpty());
            assertEquals(1, users.size());
        }

        @Test
        @DisplayName("treats emails as duplicates regardless of case and whitespace")
        void duplicateEmailDifferentCase_returnsEmpty() {
            registerAlice();

            assertTrue(auth.register("bob", "  ALICE@EXAMPLE.COM ", "otherPass1").isEmpty());
        }

        @Test
        @DisplayName("rejects a null or blank username")
        void invalidUsername_throws() {
            assertAll(
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> auth.register(null, EMAIL, PASSWORD)),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> auth.register("   ", EMAIL, PASSWORD)));

            assertEquals(0, users.size());
        }

        @Test
        @DisplayName("rejects a null or blank email")
        void invalidEmail_throws() {
            assertAll(
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> auth.register(USERNAME, null, PASSWORD)),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> auth.register(USERNAME, "  ", PASSWORD)));

            assertEquals(0, users.size());
        }

        @Test
        @DisplayName("rejects a null or blank password")
        void invalidPassword_throws() {
            assertAll(
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> auth.register(USERNAME, EMAIL, null)),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> auth.register(USERNAME, EMAIL, "   ")));

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
            auth.register("alice", "alice@example.com", "alicePass1");
            User bob = auth.register("bob", "bob@example.com", "bobPass22").orElseThrow();

            Optional<User> result = auth.login("bob@example.com", "bobPass22");

            assertTrue(result.isPresent());
            assertEquals(bob.id(), result.get().id());
            assertTrue(auth.login("bob@example.com", "alicePass1").isEmpty());
        }
    }
}
