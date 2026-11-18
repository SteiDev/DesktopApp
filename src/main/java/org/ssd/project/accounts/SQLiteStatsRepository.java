package org.ssd.project.accounts;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SQLiteStatsRepository implements IStatsRepository {
    private static final Logger log = LoggerFactory.getLogger(SQLiteStatsRepository.class);

    private static final String UPDATE_QUIZZES =
            "UPDATE stats SET quizzes_completed = ? WHERE user_id = ?";
    private static final String SELECT_QUIZZES =
            "SELECT quizzes_completed FROM stats WHERE user_id = ?";
    private static final String UPDATE_ACCURACY = "UPDATE stats SET accuracy = ? WHERE user_id = ?";
    private static final String SELECT_ACCURACY = "SELECT accuracy FROM stats WHERE user_id = ?";

    private final SQLiteDatabase db;

    public SQLiteStatsRepository(SQLiteDatabase db) {
        this.db = db;
    }

    @Override
    public void setQuizzesCompleted(long userId, int quizzesCompleted) {
        if (quizzesCompleted < 0) {
            log.warn(
                    "Rejected negative quizzesCompleted={} for user id={}",
                    quizzesCompleted,
                    userId);
            throw new IllegalArgumentException("quizzesCompleted must not be negative");
        }
        try (Connection conn = db.connect();
                PreparedStatement ps = conn.prepareStatement(UPDATE_QUIZZES)) {
            ps.setInt(1, quizzesCompleted);
            ps.setLong(2, userId);
            requireRowUpdated(ps.executeUpdate(), userId);
        } catch (SQLException e) {
            log.error("Database error while updating quizzes_completed: {}", e.toString());
            throw new IllegalStateException("Database error while updating stats", e);
        }
    }

    @Override
    public int getQuizzesCompleted(long userId) {
        try (Connection conn = db.connect();
                PreparedStatement ps = conn.prepareStatement(SELECT_QUIZZES)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return requireRow(rs, userId).getInt(1);
            }
        } catch (SQLException e) {
            log.error("Database error while reading quizzes_completed: {}", e.toString());
            throw new IllegalStateException("Database error while reading stats", e);
        }
    }

    @Override
    public void setAccuracy(long userId, double accuracy) {
        if (Double.isNaN(accuracy) || accuracy < 0.0 || accuracy > 1.0) {
            log.warn("Rejected accuracy={} for user id={}", accuracy, userId);
            throw new IllegalArgumentException("accuracy must be between 0.0 and 1.0");
        }
        try (Connection conn = db.connect();
                PreparedStatement ps = conn.prepareStatement(UPDATE_ACCURACY)) {
            ps.setDouble(1, accuracy);
            ps.setLong(2, userId);
            requireRowUpdated(ps.executeUpdate(), userId);
        } catch (SQLException e) {
            log.error("Database error while updating accuracy: {}", e.toString());
            throw new IllegalStateException("Database error while updating stats", e);
        }
    }

    @Override
    public double getAccuracy(long userId) {
        try (Connection conn = db.connect();
                PreparedStatement ps = conn.prepareStatement(SELECT_ACCURACY)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return requireRow(rs, userId).getDouble(1);
            }
        } catch (SQLException e) {
            log.error("Database error while reading accuracy: {}", e.toString());
            throw new IllegalStateException("Database error while reading stats", e);
        }
    }

    /* Fails loudly if the user has no stats row */
    private static void requireRowUpdated(int rowsUpdated, long userId) {
        if (rowsUpdated == 0) {
            log.error("No updated stats for user id={}", userId);
            throw new IllegalStateException("No stats for user id=" + userId);
        }
    }

    private static ResultSet requireRow(ResultSet rs, long userId) throws SQLException {
        if (!rs.next()) {
            log.error("No stats for user id={}", userId);
            throw new IllegalStateException("No stats for user id=" + userId);
        }
        return rs;
    }
}
