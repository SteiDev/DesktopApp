package org.ssd.project.accounts;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ssd.project.email.IEmailService;

/** Registration and login. Owns input validation, email normalization and password hashing. */
public final class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final IUserRepository users;
    private final IPasswordService passwords;
    private final IEmailService emails;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "Collaborators are injected services, not data, and are meant to be shared")
    public AuthService(IUserRepository users, IPasswordService passwords, IEmailService emails) {
        this.users = users;
        this.passwords = passwords;
        this.emails = emails;
    }

    /* Keeps your existing tests working: no email is sent */
    public AuthService(IUserRepository users, IPasswordService passwords) {
        this(users, passwords, user -> {});
    }

    public AuthService(IUserRepository users, IEmailService emails) {
        this(users, new BCryptPasswordService(), emails);
    }

    /**
     * @return the new user, or empty if the email is already registered
     */
    public Optional<User> register(String username, String email, String rawPassword) {
        requireNonBlank(username, "username");
        requireNonBlank(email, "email");
        requireNonBlank(rawPassword, "password");

        Optional<User> created =
                users.create(username, normalizeEmail(email), passwords.hash(rawPassword));
        created.ifPresent(this::sendWelcomeEmail);
        return created;
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

    public Optional<User> changeUsername(long userId, String newUsername) {
        requireNonBlank(newUsername, "username");
        return users.updateUsername(userId, newUsername);
    }

    public Optional<User> changePassword(
            long userId, String currentRawPassword, String newRawPassword) {
        requireNonBlank(newRawPassword, "new password");

        Optional<User> user = users.findById(userId);
        if (user.isEmpty()
                || currentRawPassword == null
                || !passwords.verify(currentRawPassword, user.get().passwordHash())) {
            log.warn("Rejected password change for user id={}", userId);
            return Optional.empty();
        }
        return users.updatePasswordHash(userId, passwords.hash(newRawPassword));
    }

    private void sendWelcomeEmail(User user) {
        try {
            emails.sendWelcomeEmail(user);
        } catch (RuntimeException e) {
            /* The account exists already; a failed email must not fail the registration */
            log.error("Welcome email failed for user id={}: {}", user.id(), e.toString());
        }
    }

    public boolean deleteById(long id) {
        return users.deleteById(id);
    }
}
