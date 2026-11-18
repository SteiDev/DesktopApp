package org.ssd.project.accounts;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registration and login. Owns input validation, email normalization and password hashing. */
public final class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final IUserRepository users;
    private final IPasswordService passwords;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "Collaborators are injected services, not data, and are meant to be shared")
    public AuthService(IUserRepository users, IPasswordService passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    public AuthService(IUserRepository users) {
        this(users, new BCryptPasswordService());
    }

    /**
     * @return the new user, or empty if the email is already registered
     */
    public Optional<User> register(String username, String email, String rawPassword) {
        requireNonBlank(username, "username");
        requireNonBlank(email, "email");
        requireNonBlank(rawPassword, "password");

        return users.create(username, normalizeEmail(email), passwords.hash(rawPassword));
    }

    /**
     * @return the user if the credentials match, otherwise empty
     */
    public Optional<User> login(String email, String rawPassword) {
        Optional<User> found = users.findByEmail(normalizeEmail(email));

        /* No account with that email exists */
        if (found.isEmpty()) {
            log.warn("Failed login: unknown account");
            return Optional.empty();
        }
        User user = found.get();

        /* Passwords not matching */
        if (rawPassword == null || !passwords.verify(rawPassword, user.passwordHash())) {
            log.warn("Failed login: wrong password for user id={}", user.id());
            return Optional.empty();
        }
        log.info("Login OK for user id={}", user.id());
        return found;
    }

    /* Trims and makes the email lowercase */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            log.warn("Rejected blank or null {}", name);
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }
}
