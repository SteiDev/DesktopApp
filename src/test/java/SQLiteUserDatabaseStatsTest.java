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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteConfig;
import org.ssd.project.accounts.SQLiteUserDatabase;
import org.ssd.project.accounts.User;

/** Tests for the stats row creation, getters/setters and the related input validation. */
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

    private User createUser(String name, String email) {
        return db.saveUser(name, email, PASSWORD).orElseThrow();
    }

    private int count(String table) throws SQLException {
        try (Connection conn = rawConnection();
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private void deleteStatsRow(long userId) throws SQLException {
        try (Connection conn = rawConnection();
                Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM stats WHERE user_id = " + userId);
        }
    }

    /* ---------- stats row creation in saveUser ---------- */

    @Test
    @DisplayName("saveUser creates a stats row with 0 quizzes and 0.0 accuracy")
    void saveUser_createsStatsRowWithDefaults() throws SQLException {
        User user = createUser("alice", "alice@example.com");

        assertEquals(1, count("stats"));
        assertEquals(0, db.getQuizzesCompleted(user.id()));
        assertEquals(0.0, db.getAccuracy(user.id()), DELTA);
    }

    @Test
    @DisplayName("every saved user gets their own stats row")
    void saveUser_eachUserGetsStatsRow() throws SQLException {
        createUser("alice", "alice@example.com");
        createUser("bob", "bob@example.com");

        assertEquals(2, count("users"));
        assertEquals(2, count("stats"));
    }

    @Test
    @DisplayName("a rejected duplicate email leaves no extra user or stats row behind")
    void saveUser_duplicateEmail_createsNothing() throws SQLException {
        createUser("alice", "alice@example.com");

        assertTrue(db.saveUser("bob", "alice@example.com", PASSWORD).isEmpty());

        assertEquals(1, count("users"));
        assertEquals(1, count("stats"));
    }

    /* ---------- saveUser validation ---------- */

    @Test
    @DisplayName("saveUser rejects a null or blank username")
    void saveUser_invalidUsername_throws() throws SQLException {
        assertThrows(
                IllegalArgumentException.class,
                () -> db.saveUser(null, "alice@example.com", PASSWORD));
        assertThrows(
                IllegalArgumentException.class,
                () -> db.saveUser("   ", "alice@example.com", PASSWORD));

        assertEquals(0, count("users"));
    }

    @Test
    @DisplayName("saveUser rejects a null or blank email")
    void saveUser_invalidEmail_throws() throws SQLException {
        assertThrows(IllegalArgumentException.class, () -> db.saveUser("alice", null, PASSWORD));
        assertThrows(IllegalArgumentException.class, () -> db.saveUser("alice", "  ", PASSWORD));

        assertEquals(0, count("users"));
    }

    /* ---------- quizzes completed ---------- */

    @Test
    @DisplayName("setQuizzesCompleted then getQuizzesCompleted returns the stored value")
    void quizzesCompleted_roundTrip() {
        User user = createUser("alice", "alice@example.com");

        db.setQuizzesCompleted(user.id(), 7);

        assertEquals(7, db.getQuizzesCompleted(user.id()));
    }

    @Test
    @DisplayName("setQuizzesCompleted overwrites the previous value")
    void quizzesCompleted_overwrite() {
        User user = createUser("alice", "alice@example.com");

        db.setQuizzesCompleted(user.id(), 3);
        db.setQuizzesCompleted(user.id(), 10);

        assertEquals(10, db.getQuizzesCompleted(user.id()));
    }

    @Test
    @DisplayName("quizzes completed supports zero and large values")
    void quizzesCompleted_boundaries() {
        User user = createUser("alice", "alice@example.com");

        db.setQuizzesCompleted(user.id(), 0);
        assertEquals(0, db.getQuizzesCompleted(user.id()));

        db.setQuizzesCompleted(user.id(), Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, db.getQuizzesCompleted(user.id()));
    }

    @Test
    @DisplayName("setQuizzesCompleted rejects a negative value and keeps the old one")
    void quizzesCompleted_negative_throws() {
        User user = createUser("alice", "alice@example.com");
        db.setQuizzesCompleted(user.id(), 4);

        assertThrows(IllegalArgumentException.class, () -> db.setQuizzesCompleted(user.id(), -1));

        assertEquals(4, db.getQuizzesCompleted(user.id()));
    }

    /* ---------- accuracy ---------- */

    @Test
    @DisplayName("setAccuracy then getAccuracy returns the stored value")
    void accuracy_roundTrip() {
        User user = createUser("alice", "alice@example.com");

        db.setAccuracy(user.id(), 0.85);

        assertEquals(0.85, db.getAccuracy(user.id()), DELTA);
    }

    @Test
    @DisplayName("setAccuracy overwrites the previous value")
    void accuracy_overwrite() {
        User user = createUser("alice", "alice@example.com");

        db.setAccuracy(user.id(), 0.25);
        db.setAccuracy(user.id(), 0.9);

        assertEquals(0.9, db.getAccuracy(user.id()), DELTA);
    }

    @Test
    @DisplayName("accuracy supports the boundaries 0.0 and 1.0")
    void accuracy_boundaries() {
        User user = createUser("alice", "alice@example.com");

        db.setAccuracy(user.id(), 1.0);
        assertEquals(1.0, db.getAccuracy(user.id()), DELTA);

        db.setAccuracy(user.id(), 0.0);
        assertEquals(0.0, db.getAccuracy(user.id()), DELTA);
    }

    @Test
    @DisplayName("accuracy keeps fractional precision")
    void accuracy_keepsPrecision() {
        User user = createUser("alice", "alice@example.com");

        db.setAccuracy(user.id(), 2.0 / 3.0);

        assertEquals(2.0 / 3.0, db.getAccuracy(user.id()), DELTA);
    }

    @Test
    @DisplayName("setAccuracy rejects values outside 0.0-1.0 and NaN, keeping the old value")
    void accuracy_invalid_throws() {
        User user = createUser("alice", "alice@example.com");
        db.setAccuracy(user.id(), 0.5);

        assertThrows(IllegalArgumentException.class, () -> db.setAccuracy(user.id(), -0.1));
        assertThrows(IllegalArgumentException.class, () -> db.setAccuracy(user.id(), 1.1));
        assertThrows(IllegalArgumentException.class, () -> db.setAccuracy(user.id(), Double.NaN));

        assertEquals(0.5, db.getAccuracy(user.id()), DELTA);
    }

    /* ---------- independence ---------- */

    @Test
    @DisplayName("updating accuracy does not change quizzes completed, and vice versa")
    void fields_areIndependent() {
        User user = createUser("alice", "alice@example.com");

        db.setQuizzesCompleted(user.id(), 4);
        db.setAccuracy(user.id(), 0.5);
        db.setAccuracy(user.id(), 0.75);

        assertEquals(4, db.getQuizzesCompleted(user.id()));

        db.setQuizzesCompleted(user.id(), 9);

        assertEquals(0.75, db.getAccuracy(user.id()), DELTA);
    }

    @Test
    @DisplayName("stats belong to one user and do not leak to others")
    void stats_arePerUser() {
        User alice = createUser("alice", "alice@example.com");
        User bob = createUser("bob", "bob@example.com");

        db.setQuizzesCompleted(alice.id(), 5);
        db.setAccuracy(alice.id(), 0.9);

        assertEquals(0, db.getQuizzesCompleted(bob.id()));
        assertEquals(0.0, db.getAccuracy(bob.id()), DELTA);
        assertEquals(5, db.getQuizzesCompleted(alice.id()));
        assertEquals(0.9, db.getAccuracy(alice.id()), DELTA);
    }

    /* ---------- missing rows ---------- */

    @Test
    @DisplayName("getters throw for a user id that does not exist")
    void getters_unknownUser_throw() {
        assertThrows(IllegalStateException.class, () -> db.getQuizzesCompleted(9999L));
        assertThrows(IllegalStateException.class, () -> db.getAccuracy(9999L));
    }

    @Test
    @DisplayName("setters throw for a user id that does not exist")
    void setters_unknownUser_throw() {
        assertThrows(IllegalStateException.class, () -> db.setQuizzesCompleted(9999L, 3));
        assertThrows(IllegalStateException.class, () -> db.setAccuracy(9999L, 0.5));
    }

    @Test
    @DisplayName("getters and setters throw if the stats row is missing for an existing user")
    void statsRowMissing_throws() throws SQLException {
        User user = createUser("alice", "alice@example.com");
        deleteStatsRow(user.id());

        assertThrows(IllegalStateException.class, () -> db.getQuizzesCompleted(user.id()));
        assertThrows(IllegalStateException.class, () -> db.getAccuracy(user.id()));
        assertThrows(IllegalStateException.class, () -> db.setQuizzesCompleted(user.id(), 3));
        assertThrows(IllegalStateException.class, () -> db.setAccuracy(user.id(), 0.5));
        assertEquals(0, count("stats"));
    }

    /* ---------- persistence and deletion ---------- */

    @Test
    @DisplayName("stats persist across repository instances on the same file")
    void stats_persistAcrossInstances() {
        User user = createUser("alice", "alice@example.com");
        db.setQuizzesCompleted(user.id(), 12);
        db.setAccuracy(user.id(), 0.6);

        SQLiteUserDatabase reopened = new SQLiteUserDatabase(dbFile.toString());

        assertEquals(12, reopened.getQuizzesCompleted(user.id()));
        assertEquals(0.6, reopened.getAccuracy(user.id()), DELTA);
    }

    @Test
    @DisplayName("deleting a user removes their stats, so the getters throw afterwards")
    void deleteUser_removesStats() throws SQLException {
        User user = createUser("alice", "alice@example.com");
        db.setQuizzesCompleted(user.id(), 5);

        assertTrue(db.deleteUser(user.id()));

        assertEquals(0, count("stats"));
        assertThrows(IllegalStateException.class, () -> db.getQuizzesCompleted(user.id()));
        assertThrows(IllegalStateException.class, () -> db.getAccuracy(user.id()));
    }

    @Test
    @DisplayName("deleting one user keeps the stats of the others")
    void deleteUser_keepsOtherUsersStats() throws SQLException {
        User alice = createUser("alice", "alice@example.com");
        User bob = createUser("bob", "bob@example.com");
        db.setQuizzesCompleted(bob.id(), 8);

        db.deleteUser(alice.id());

        assertEquals(8, db.getQuizzesCompleted(bob.id()));
        assertEquals(1, count("stats"));
    }
}
