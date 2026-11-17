import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConfig;
import org.ssd.project.accounts.SQLiteUserDatabase;
import org.ssd.project.accounts.User;

/** Tests for the stats row lifecycle, the getters/setters and their input validation. */
class SQLiteUserDatabaseStatsTest {

    private static final String PASSWORD = "S3cret!pass";
    private static final double DELTA = 1e-9;

    @TempDir Path tempDir;

    private Path dbFile;
    private SQLiteUserDatabase db;

    @BeforeEach
    void setUp() {
        dbFile = tempDir.resolve("stats-test.db");
        db = new SQLiteUserDatabase(dbFile.toString());
    }

    /* ---------- helpers ---------- */

    private Connection rawConnection() throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        return DriverManager.getConnection("jdbc:sqlite:" + dbFile, config.toProperties());
    }

    private void executeRaw(String sql) throws SQLException {
        try (Connection conn = rawConnection();
                Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private int count(String table) throws SQLException {
        try (Connection conn = rawConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private User createUser(String name, String email) {
        return db.saveUser(name, email, PASSWORD).orElseThrow();
    }

    private User createAlice() {
        return createUser("alice", "alice@example.com");
    }

    /* ---------- stats row creation ---------- */

    @Nested
    @DisplayName("stats row creation")
    class Creation {

        @Test
        @DisplayName("saveUser creates a stats row with 0 quizzes and 0.0 accuracy")
        void createsRowWithDefaults() throws SQLException {
            User user = createAlice();

            assertAll(
                    () -> assertEquals(1, count("stats")),
                    () -> assertEquals(0, db.getQuizzesCompleted(user.id())),
                    () -> assertEquals(0.0, db.getAccuracy(user.id()), DELTA));
        }

        @Test
        @DisplayName("every saved user gets their own stats row")
        void eachUserGetsRow() throws SQLException {
            createAlice();
            createUser("bob", "bob@example.com");

            assertAll(() -> assertEquals(2, count("users")), () -> assertEquals(2, count("stats")));
        }

        @Test
        @DisplayName("a rejected duplicate email leaves no extra user or stats row")
        void duplicateEmail_createsNothing() throws SQLException {
            createAlice();

            assertTrue(db.saveUser("bob", "alice@example.com", PASSWORD).isEmpty());

            assertAll(() -> assertEquals(1, count("users")), () -> assertEquals(1, count("stats")));
        }

        @Test
        @DisplayName("if the stats insert fails, the user insert is rolled back too")
        void statsInsertFails_rollsBackUser() throws SQLException {
            executeRaw(
                    "CREATE TRIGGER fail_stats_insert BEFORE INSERT ON stats "
                            + "BEGIN SELECT RAISE(ABORT, 'simulated stats failure'); END");

            assertThrows(
                    IllegalStateException.class,
                    () -> db.saveUser("alice", "alice@example.com", PASSWORD));

            assertAll(() -> assertEquals(0, count("users")), () -> assertEquals(0, count("stats")));
        }
    }

    /* ---------- quizzes completed ---------- */

    @Nested
    @DisplayName("quizzes completed")
    class QuizzesCompleted {

        @Test
        @DisplayName("set then get returns the stored value")
        void roundTrip() {
            User user = createAlice();

            db.setQuizzesCompleted(user.id(), 7);

            assertEquals(7, db.getQuizzesCompleted(user.id()));
        }

        @Test
        @DisplayName("set overwrites the previous value")
        void overwrite() {
            User user = createAlice();

            db.setQuizzesCompleted(user.id(), 3);
            db.setQuizzesCompleted(user.id(), 10);

            assertEquals(10, db.getQuizzesCompleted(user.id()));
        }

        @Test
        @DisplayName("supports zero and Integer.MAX_VALUE")
        void boundaries() {
            User user = createAlice();

            db.setQuizzesCompleted(user.id(), Integer.MAX_VALUE);
            assertEquals(Integer.MAX_VALUE, db.getQuizzesCompleted(user.id()));

            db.setQuizzesCompleted(user.id(), 0);
            assertEquals(0, db.getQuizzesCompleted(user.id()));
        }

        @Test
        @DisplayName("rejects a negative value and keeps the old one")
        void negative_throws() {
            User user = createAlice();
            db.setQuizzesCompleted(user.id(), 4);

            assertThrows(
                    IllegalArgumentException.class, () -> db.setQuizzesCompleted(user.id(), -1));

            assertEquals(4, db.getQuizzesCompleted(user.id()));
        }
    }

    /* ---------- accuracy ---------- */

    @Nested
    @DisplayName("accuracy")
    class Accuracy {

        @Test
        @DisplayName("set then get returns the stored value")
        void roundTrip() {
            User user = createAlice();

            db.setAccuracy(user.id(), 0.85);

            assertEquals(0.85, db.getAccuracy(user.id()), DELTA);
        }

        @Test
        @DisplayName("set overwrites the previous value")
        void overwrite() {
            User user = createAlice();

            db.setAccuracy(user.id(), 0.25);
            db.setAccuracy(user.id(), 0.9);

            assertEquals(0.9, db.getAccuracy(user.id()), DELTA);
        }

        @Test
        @DisplayName("supports the boundaries 0.0 and 1.0")
        void boundaries() {
            User user = createAlice();

            db.setAccuracy(user.id(), 1.0);
            assertEquals(1.0, db.getAccuracy(user.id()), DELTA);

            db.setAccuracy(user.id(), 0.0);
            assertEquals(0.0, db.getAccuracy(user.id()), DELTA);
        }

        @Test
        @DisplayName("keeps fractional precision")
        void keepsPrecision() {
            User user = createAlice();

            db.setAccuracy(user.id(), 2.0 / 3.0);

            assertEquals(2.0 / 3.0, db.getAccuracy(user.id()), DELTA);
        }

        @Test
        @DisplayName("rejects values outside 0.0-1.0, NaN and infinity, keeping the old value")
        void invalid_throws() {
            User user = createAlice();
            db.setAccuracy(user.id(), 0.5);

            assertAll(
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> db.setAccuracy(user.id(), -0.1)),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> db.setAccuracy(user.id(), 1.1)),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> db.setAccuracy(user.id(), Double.NaN)),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> db.setAccuracy(user.id(), Double.POSITIVE_INFINITY)),
                    () ->
                            assertThrows(
                                    IllegalArgumentException.class,
                                    () -> db.setAccuracy(user.id(), Double.NEGATIVE_INFINITY)));

            assertEquals(0.5, db.getAccuracy(user.id()), DELTA);
        }
    }

    /* ---------- independence ---------- */

    @Nested
    @DisplayName("independence")
    class Independence {

        @Test
        @DisplayName("updating one field does not change the other")
        void fieldsAreIndependent() {
            User user = createAlice();

            db.setQuizzesCompleted(user.id(), 4);
            db.setAccuracy(user.id(), 0.75);
            assertEquals(4, db.getQuizzesCompleted(user.id()));

            db.setQuizzesCompleted(user.id(), 9);
            assertEquals(0.75, db.getAccuracy(user.id()), DELTA);
        }

        @Test
        @DisplayName("stats belong to one user and do not leak to others")
        void statsArePerUser() {
            User alice = createAlice();
            User bob = createUser("bob", "bob@example.com");

            db.setQuizzesCompleted(alice.id(), 5);
            db.setAccuracy(alice.id(), 0.9);

            assertAll(
                    () -> assertEquals(0, db.getQuizzesCompleted(bob.id())),
                    () -> assertEquals(0.0, db.getAccuracy(bob.id()), DELTA),
                    () -> assertEquals(5, db.getQuizzesCompleted(alice.id())),
                    () -> assertEquals(0.9, db.getAccuracy(alice.id()), DELTA));
        }
    }

    /* ---------- missing rows ---------- */

    @Nested
    @DisplayName("missing stats")
    class MissingRows {

        @Test
        @DisplayName("getters and setters throw for a user id that does not exist")
        void unknownUser_throws() {
            assertAll(
                    () ->
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> db.getQuizzesCompleted(9999L)),
                    () -> assertThrows(IllegalStateException.class, () -> db.getAccuracy(9999L)),
                    () ->
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> db.setQuizzesCompleted(9999L, 3)),
                    () ->
                            assertThrows(
                                    IllegalStateException.class, () -> db.setAccuracy(9999L, 0.5)));
        }

        @Test
        @DisplayName("getters and setters throw if the stats row is gone for an existing user")
        void statsRowMissing_throws() throws SQLException {
            User user = createAlice();
            /* Simulates a corrupted or legacy database */
            executeRaw("DELETE FROM stats WHERE user_id = " + user.id());

            assertAll(
                    () ->
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> db.getQuizzesCompleted(user.id())),
                    () ->
                            assertThrows(
                                    IllegalStateException.class, () -> db.getAccuracy(user.id())),
                    () ->
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> db.setQuizzesCompleted(user.id(), 3)),
                    () ->
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> db.setAccuracy(user.id(), 0.5)));
            assertEquals(0, count("stats"));
        }
    }

    /* ---------- persistence, deletion and schema ---------- */

    @Nested
    @DisplayName("lifecycle and schema")
    class Lifecycle {

        @Test
        @DisplayName("stats persist across repository instances on the same file")
        void persistAcrossInstances() {
            User user = createAlice();
            db.setQuizzesCompleted(user.id(), 12);
            db.setAccuracy(user.id(), 0.6);

            SQLiteUserDatabase reopened = new SQLiteUserDatabase(dbFile.toString());

            assertAll(
                    () -> assertEquals(12, reopened.getQuizzesCompleted(user.id())),
                    () -> assertEquals(0.6, reopened.getAccuracy(user.id()), DELTA));
        }

        @Test
        @DisplayName("deleting a user cascades to their stats")
        void deleteUser_removesStats() throws SQLException {
            User user = createAlice();
            db.setQuizzesCompleted(user.id(), 5);

            assertTrue(db.deleteUser(user.id()));

            assertAll(
                    () -> assertEquals(0, count("stats")),
                    () ->
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> db.getQuizzesCompleted(user.id())),
                    () ->
                            assertThrows(
                                    IllegalStateException.class, () -> db.getAccuracy(user.id())));
        }

        @Test
        @DisplayName("deleting one user keeps the stats of the others")
        void deleteUser_keepsOtherUsersStats() throws SQLException {
            User alice = createAlice();
            User bob = createUser("bob", "bob@example.com");
            db.setQuizzesCompleted(bob.id(), 8);

            db.deleteUser(alice.id());

            assertAll(
                    () -> assertEquals(8, db.getQuizzesCompleted(bob.id())),
                    () -> assertEquals(1, count("stats")));
        }

        @Test
        @DisplayName("the schema rejects a stats row for an unknown user (foreign key)")
        void foreignKey_rejectsUnknownUser() {
            assertThrows(
                    SQLException.class,
                    () -> executeRaw("INSERT INTO stats (user_id) VALUES (9999)"));
        }
    }
}
