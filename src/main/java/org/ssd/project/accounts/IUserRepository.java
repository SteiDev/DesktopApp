package org.ssd.project.accounts;

import java.util.Optional;

/** Stores and fetches user rows. Knows nothing about passwords beyond the stored hash. */
public interface IUserRepository {
    /**
     * Inserts a new user. The caller is responsible for normalizing the email and hashing the
     * password.
     *
     * @return the saved user, or empty if the email is already taken
     */
    Optional<User> create(String username, String email, String passwordHash);

    Optional<User> findByEmail(String email);

    Optional<User> findById(long id);

    Optional<User> updateUsername(long id, String newUsername);

    Optional<User> updatePasswordHash(long id, String newRawPassword);

    /**
     * @return {@code true} if a user was deleted (their stats are removed by cascade)
     */
    boolean deleteById(long id);
}
