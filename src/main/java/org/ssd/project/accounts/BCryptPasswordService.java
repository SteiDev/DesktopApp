package org.ssd.project.accounts;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Hashes and verifies passwords using BCrypt. */
public final class BCryptPasswordService implements IPasswordService {
    private static final int PASSWORD_STRENGTH = 12;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(PASSWORD_STRENGTH);

    /**
     * Hashes a raw password with a random salt.
     *
     * @param rawPassword the plain-text password; must not be null or empty
     * @return the BCrypt hash, never null
     * @throws IllegalArgumentException if {@code passwordHash} is null or empty
     */
    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("passwordHash must not be null or empty");
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
