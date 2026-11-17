package org.ssd.project.accounts;

import java.util.Optional;

public interface IUserDatabase {
    Optional<User> login(String email, String rawPassword);

    Optional<User> saveUser(String username, String email, String rawPassword);

    boolean deleteUser(long id);

    void setQuizzesCompleted(long id, int quizzesCompleted);

    int getQuizzesCompleted(long id);

    void setAccuracy(long id, double accuracy);

    double getAccuracy(long id);
}
