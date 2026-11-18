package org.ssd.project.accounts;

/** Stores and fetches per-user quiz statistics. */
public interface IStatsRepository {
    void setQuizzesCompleted(long userId, int quizzesCompleted);

    int getQuizzesCompleted(long userId);

    void setAccuracy(long userId, double accuracy);

    double getAccuracy(long userId);
}
