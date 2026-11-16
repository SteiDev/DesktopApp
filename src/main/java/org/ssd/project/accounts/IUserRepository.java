package org.ssd.project.accounts;

import java.util.Optional;

public interface IUserRepository {
    Optional<User> login(String email, String rawPassword);
    Optional<User> saveUser(String username, String email, String rawPassword);
}
