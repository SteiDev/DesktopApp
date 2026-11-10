import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.ssd.project.accounts.PasswordService;

class PasswordServiceTest {

    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService();
    }

    // ---------- hash ----------

    @Test
    void hash_returnsNonNullHashDifferentFromRawPassword() {
        String raw = "S3cret!Passw0rd";

        String hash = passwordService.hash(raw);

        assertNotNull(hash);
        assertNotEquals(raw, hash);
    }

    @Test
    void hash_usesBCryptFormatWithConfiguredStrength() {
        String hash = passwordService.hash("S3cret!Passw0rd");

        // BCrypt hashes are 60 chars: $2a$<cost>$<22 char salt><31 char hash>
        assertEquals(60, hash.length());
        assertTrue(
                hash.matches("^\\$2[aby]\\$12\\$.{53}$"),
                "Expected a BCrypt hash with cost factor 12 but got: " + hash);
    }

    @Test
    void hash_producesDifferentHashesForSamePassword_dueToRandomSalt() {
        String raw = "S3cret!Passw0rd";

        String first = passwordService.hash(raw);
        String second = passwordService.hash(raw);

        assertNotEquals(first, second);
    }

    @Test
    void hash_emptyPassword_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> passwordService.hash(""));
    }

    @Test
    void hash_handlesUnicodeAndSpecialCharacters() {
        String raw = "pässwörd-密码-🔐 \t\n";

        String hash = passwordService.hash(raw);

        assertTrue(passwordService.verify(raw, hash));
    }

    @Test
    void hash_nullPassword_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> passwordService.hash(null));
    }

    // ---------- verify ----------

    @Test
    void verify_returnsTrueForCorrectPassword() {
        String raw = "S3cret!Passw0rd";
        String hash = passwordService.hash(raw);

        assertTrue(passwordService.verify(raw, hash));
    }

    @Test
    void verify_returnsFalseForWrongPassword() {
        String hash = passwordService.hash("S3cret!Passw0rd");

        assertFalse(passwordService.verify("wrong-password", hash));
    }

    @Test
    void verify_isCaseSensitive() {
        String hash = passwordService.hash("Password123");

        assertFalse(passwordService.verify("password123", hash));
    }

    @Test
    void verify_returnsFalseForPasswordWithTrailingWhitespace() {
        String hash = passwordService.hash("Password123");

        assertFalse(passwordService.verify("Password123 ", hash));
    }

    @Test
    void verify_returnsFalseForMalformedHash() {
        assertFalse(passwordService.verify("S3cret!Passw0rd", "not-a-bcrypt-hash"));
    }

    @Test
    void verify_returnsFalseForEmptyStoredHash() {
        assertFalse(passwordService.verify("S3cret!Passw0rd", ""));
    }

    @Test
    void verify_returnsFalseForNullStoredHash() {
        assertFalse(passwordService.verify("S3cret!Passw0rd", null));
    }

    @Test
    void verify_returnsFalseForNullRawPassword() {
        String hash = passwordService.hash("S3cret!Passw0rd");

        assertFalse(passwordService.verify(null, hash));
    }

    @Test
    void verify_returnsFalseForEmptyRawPassword() {
        String hash = passwordService.hash("S3cret!Passw0rd");

        assertFalse(passwordService.verify("", hash));
    }

    @Test
    void verify_acceptsHashGeneratedByAnotherBCryptEncoderInstance() {
        String raw = "S3cret!Passw0rd";
        String externalHash = new BCryptPasswordEncoder(12).encode(raw);

        assertTrue(passwordService.verify(raw, externalHash));
    }

    @Test
    void verify_acceptsHashesWithDifferentCostFactor() {
        // Important for migrating older hashes: matches() reads cost from the hash itself
        String raw = "S3cret!Passw0rd";
        String legacyHash = new BCryptPasswordEncoder(4).encode(raw);

        assertTrue(passwordService.verify(raw, legacyHash));
    }
}
