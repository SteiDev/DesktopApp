package org.ssd.project.accounts;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ssd.project.email.IEmailService;

/** Registration and login. Owns input validation, email normalization and password hashing. */
public final class AuthService {
    private record PendingRegistration(
            String username,
            String passwordHash,
            String codeHash,
            long expiresAtMillis,
            int attempts) {
        PendingRegistration withAttempts(int n) {
            return new PendingRegistration(username, passwordHash, codeHash, expiresAtMillis, n);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final IUserRepository users;
    private final IPasswordService passwords;
    private final IEmailService emails;

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;
    private static final int CODE_LENGTH = 6;
    private static final String ALPHABET = "0123456789"; // or letters+digits for alphanumeric codes

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingRegistration> pending = new ConcurrentHashMap<>();

    public enum StartResult {
        CODE_SENT,
        EMAIL_TAKEN,
        TOO_SOON,
        NO_PENDING
    }

    public enum VerifyResult {
        OK,
        WRONG_CODE,
        EXPIRED,
        TOO_MANY_ATTEMPTS,
        NO_PENDING,
        EMAIL_TAKEN
    }

    public record VerifyOutcome(VerifyResult result, User user) {} // user is null unless OK

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "Collaborators are injected services, not data, and are meant to be shared")
    public AuthService(
            IUserRepository users, IPasswordService passwords, IEmailService emails, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.emails = emails;
        this.clock = clock;
    }

    public AuthService(IUserRepository users, IPasswordService passwords, IEmailService emails) {
        this(users, passwords, emails, Clock.systemUTC());
    }

    /* Keeps your existing tests working: no email is sent */
    public AuthService(IUserRepository users, IPasswordService passwords) {
        this(users, passwords, IEmailService.noOp(), Clock.systemUTC());
    }

    public AuthService(IUserRepository users, IEmailService emails) {
        this(users, new BCryptPasswordService(), emails, Clock.systemUTC());
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

    /**
     * Begins the email-verification registration flow.
     *
     * <p>Hashes the password immediately and stores a pending entry, then sends a 6-digit code to
     * the given address. The caller must later call {@link #completeRegistration(String, String)}
     * to create the account.
     *
     * @return {@code CODE_SENT} on success, {@code EMAIL_TAKEN} if already registered, {@code
     *     TOO_SOON} if a code was issued less than 60 s ago
     */
    public StartResult startRegistration(String username, String email, String rawPassword) {
        requireNonBlank(username, "username");
        requireNonBlank(email, "email");
        requireNonBlank(rawPassword, "password");

        String key = normalizeEmail(email);
        if (users.findByEmail(key).isPresent()) return StartResult.EMAIL_TAKEN;

        return issueCode(key, username, passwords.hash(rawPassword));
    }

    public StartResult resendCode(String email) {
        String key = normalizeEmail(email);
        PendingRegistration p = pending.get(key);
        if (p == null) return StartResult.NO_PENDING;
        return issueCode(key, p.username(), p.passwordHash());
    }

    private synchronized StartResult issueCode(String key, String username, String passwordHash) {
        long now = clock.millis();
        pending.values().removeIf(p -> now > p.expiresAtMillis()); // sweep expired entries

        PendingRegistration existing = pending.get(key);
        if (existing != null) {
            long issuedAt = existing.expiresAtMillis() - CODE_TTL.toMillis();
            if (now - issuedAt < RESEND_COOLDOWN.toMillis()) return StartResult.TOO_SOON;
        }

        String code = generateCode();
        pending.put(
                key,
                new PendingRegistration(
                        username,
                        passwordHash,
                        passwords.hash(code),
                        now + CODE_TTL.toMillis(),
                        0));
        try {
            emails.sendVerificationEmail(username, key, code);
        } catch (RuntimeException e) {
            log.error("Verification email failed: {}", e.toString()); // never log the code
        }
        return StartResult.CODE_SENT;
    }

    public VerifyOutcome completeRegistration(String email, String code) {
        String key = normalizeEmail(email);
        PendingRegistration p = pending.get(key);
        if (p == null) return new VerifyOutcome(VerifyResult.NO_PENDING, null);

        if (clock.millis() > p.expiresAtMillis()) {
            pending.remove(key);
            return new VerifyOutcome(VerifyResult.EXPIRED, null);
        }

        // Count the attempt before checking, so parallel guesses can't dodge the limit
        PendingRegistration updated =
                pending.computeIfPresent(key, (k, v) -> v.withAttempts(v.attempts() + 1));
        if (updated == null) return new VerifyOutcome(VerifyResult.NO_PENDING, null);
        if (updated.attempts() > MAX_ATTEMPTS) {
            pending.remove(key); // must start over
            return new VerifyOutcome(VerifyResult.TOO_MANY_ATTEMPTS, null);
        }
        if (code == null || !passwords.verify(code.trim(), updated.codeHash())) {
            return new VerifyOutcome(VerifyResult.WRONG_CODE, null);
        }

        pending.remove(key);
        // The only place a user row is created. Empty means someone took the email meanwhile.
        Optional<User> created = users.create(updated.username(), key, updated.passwordHash());
        created.ifPresent(this::sendWelcomeEmail); // your existing method
        return created.map(u -> new VerifyOutcome(VerifyResult.OK, u))
                .orElse(new VerifyOutcome(VerifyResult.EMAIL_TAKEN, null));
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
