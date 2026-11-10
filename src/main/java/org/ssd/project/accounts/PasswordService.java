package org.ssd.project.accounts;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Hashes and verifies passwords using BCrypt. */
public class PasswordService {
    private static final int PASSWORD_STREGTH = 12;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(PASSWORD_STREGTH);

    /**
     * Hashes a raw password with a random salt.
     *
     * @param rawPassword the plain-text password; must not be null or empty
     * @return the BCrypt hash, never null
     * @throws IllegalArgumentException if {@code rawPassword} is null or empty
     */
    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("rawPassword must not be null or empty");
        }

        return encoder.encode(rawPassword);
    }

    /**
     * Checks a raw password against a stored hash.
     *
     * @param rawPassword the plain-text password to check
     * @param storedHash the hash previously returned by {@link #hash(String)}
     * @return {@code true} if they match; {@code false} on mismatch or if either argument is null
     *     or empty
     */
    public boolean verify(String rawPassword, String storedHash) {
        if (rawPassword == null
                || rawPassword.isEmpty()
                || storedHash == null
                || storedHash.isEmpty()) {
            return false;
        }
        return encoder.matches(rawPassword, storedHash);
    }
}
