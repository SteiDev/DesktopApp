package org.ssd.project.email;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SmtpConfig} validation and {@link SmtpConfig#fromEnvironment}. */
class SmtpConfigTest {

    /* ------------------------------------------------------------------ constructor validation */

    @Nested
    @DisplayName("constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("accepts valid minimal config")
        void validConfig_accepted() {
            SmtpConfig cfg =
                    new SmtpConfig(
                            "smtp.example.com",
                            587,
                            null,
                            null,
                            "no-reply@example.com",
                            SmtpConfig.Security.STARTTLS);

            assertAll(
                    () -> assertEquals("smtp.example.com", cfg.host()),
                    () -> assertEquals(587, cfg.port()),
                    () -> assertEquals("no-reply@example.com", cfg.from()),
                    () -> assertEquals(SmtpConfig.Security.STARTTLS, cfg.security()));
        }

        @Test
        @DisplayName("rejects a null host")
        void nullHost_throws() {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SmtpConfig(
                                    null,
                                    587,
                                    null,
                                    null,
                                    "from@example.com",
                                    SmtpConfig.Security.NONE));
        }

        @Test
        @DisplayName("rejects a blank host")
        void blankHost_throws() {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SmtpConfig(
                                    "  ",
                                    587,
                                    null,
                                    null,
                                    "from@example.com",
                                    SmtpConfig.Security.NONE));
        }

        @Test
        @DisplayName("rejects a null from-address")
        void nullFrom_throws() {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SmtpConfig(
                                    "smtp.example.com",
                                    587,
                                    null,
                                    null,
                                    null,
                                    SmtpConfig.Security.NONE));
        }

        @Test
        @DisplayName("rejects a blank from-address")
        void blankFrom_throws() {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SmtpConfig(
                                    "smtp.example.com",
                                    587,
                                    null,
                                    null,
                                    "   ",
                                    SmtpConfig.Security.NONE));
        }

        @Test
        @DisplayName("rejects port 0")
        void portZero_throws() {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SmtpConfig(
                                    "smtp.example.com",
                                    0,
                                    null,
                                    null,
                                    "from@example.com",
                                    SmtpConfig.Security.NONE));
        }

        @Test
        @DisplayName("rejects port 65536")
        void portTooHigh_throws() {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SmtpConfig(
                                    "smtp.example.com",
                                    65_536,
                                    null,
                                    null,
                                    "from@example.com",
                                    SmtpConfig.Security.NONE));
        }

        @Test
        @DisplayName("accepts port boundary values 1 and 65535")
        void portBoundaries_accepted() {
            assertAll(
                    () ->
                            assertEquals(
                                    1,
                                    new SmtpConfig(
                                                    "h",
                                                    1,
                                                    null,
                                                    null,
                                                    "f@e.com",
                                                    SmtpConfig.Security.NONE)
                                            .port()),
                    () ->
                            assertEquals(
                                    65_535,
                                    new SmtpConfig(
                                                    "h",
                                                    65_535,
                                                    null,
                                                    null,
                                                    "f@e.com",
                                                    SmtpConfig.Security.NONE)
                                            .port()));
        }

        @Test
        @DisplayName("rejects a null security")
        void nullSecurity_throws() {
            assertThrows(
                    NullPointerException.class,
                    () ->
                            new SmtpConfig(
                                    "smtp.example.com", 587, null, null, "from@example.com", null));
        }
    }

    /* ------------------------------------------------------------------ hasCredentials */

    @Nested
    @DisplayName("hasCredentials")
    class HasCredentials {

        @Test
        @DisplayName("returns true when username is set")
        void withUsername_true() {
            SmtpConfig cfg =
                    new SmtpConfig("h", 25, "user", "pass", "f@e.com", SmtpConfig.Security.NONE);
            assertTrue(cfg.hasCredentials());
        }

        @Test
        @DisplayName("returns false when username is null")
        void nullUsername_false() {
            SmtpConfig cfg =
                    new SmtpConfig("h", 25, null, null, "f@e.com", SmtpConfig.Security.NONE);
            assertFalse(cfg.hasCredentials());
        }

        @Test
        @DisplayName("returns false when username is blank")
        void blankUsername_false() {
            SmtpConfig cfg =
                    new SmtpConfig("h", 25, "  ", null, "f@e.com", SmtpConfig.Security.NONE);
            assertFalse(cfg.hasCredentials());
        }
    }

    /* ------------------------------------------------------------------ fromEnvironment */

    @Nested
    @DisplayName("fromEnvironment")
    class FromEnvironment {

        private SmtpConfig fromEnv(Map<String, String> env) {
            return SmtpConfig.fromEnvironment(env::get);
        }

        @Test
        @DisplayName("reads all variables correctly")
        void allVarsSet_parsedCorrectly() {
            SmtpConfig cfg =
                    fromEnv(
                            Map.of(
                                    "SMTP_HOST", "mail.example.com",
                                    "SMTP_PORT", "465",
                                    "SMTP_USERNAME", "user@example.com",
                                    "SMTP_PASSWORD", "s3cret",
                                    "SMTP_FROM", "noreply@example.com",
                                    "SMTP_FROM_NAME", "My App",
                                    "SMTP_SECURITY", "SSL"));

            assertAll(
                    () -> assertEquals("mail.example.com", cfg.host()),
                    () -> assertEquals(465, cfg.port()),
                    () -> assertEquals("user@example.com", cfg.username()),
                    () -> assertEquals("s3cret", cfg.password()),
                    () -> assertEquals("noreply@example.com", cfg.from()),
                    () -> assertEquals("My App", cfg.fromName()),
                    () -> assertEquals(SmtpConfig.Security.SSL, cfg.security()));
        }

        @Test
        @DisplayName("defaults to port 587 when SMTP_PORT is absent")
        void missingPort_defaultsTo587() {
            SmtpConfig cfg =
                    fromEnv(
                            Map.of(
                                    "SMTP_HOST", "h",
                                    "SMTP_FROM", "f@e.com",
                                    "SMTP_SECURITY", "NONE"));
            assertEquals(587, cfg.port());
        }

        @Test
        @DisplayName("defaults to STARTTLS when SMTP_SECURITY is absent")
        void missingSecurity_defaultsToStarttls() {
            SmtpConfig cfg =
                    fromEnv(Map.of("SMTP_HOST", "h", "SMTP_FROM", "f@e.com", "SMTP_PORT", "587"));
            assertEquals(SmtpConfig.Security.STARTTLS, cfg.security());
        }

        @Test
        @DisplayName("security is case-insensitive")
        void securityCaseInsensitive() {
            SmtpConfig cfg =
                    fromEnv(
                            Map.of(
                                    "SMTP_HOST", "h",
                                    "SMTP_FROM", "f@e.com",
                                    "SMTP_PORT", "25",
                                    "SMTP_SECURITY", "none"));
            assertEquals(SmtpConfig.Security.NONE, cfg.security());
        }

        @Test
        @DisplayName("port variable is trimmed before parsing")
        void portWithWhitespace_parsed() {
            SmtpConfig cfg =
                    fromEnv(
                            Map.of(
                                    "SMTP_HOST", "h",
                                    "SMTP_FROM", "f@e.com",
                                    "SMTP_PORT", " 25 ",
                                    "SMTP_SECURITY", "NONE"));
            assertEquals(25, cfg.port());
        }

        @Test
        @DisplayName("throws when SMTP_HOST is missing")
        void missingHost_throws() {
            assertThrows(
                    IllegalArgumentException.class, () -> fromEnv(Map.of("SMTP_FROM", "f@e.com")));
        }

        @Test
        @DisplayName("does not expose password in toString")
        void toString_omitsPassword() {
            SmtpConfig cfg =
                    fromEnv(
                            Map.of(
                                    "SMTP_HOST", "h",
                                    "SMTP_PORT", "587",
                                    "SMTP_FROM", "f@e.com",
                                    "SMTP_USERNAME", "u",
                                    "SMTP_PASSWORD", "supersecret",
                                    "SMTP_SECURITY", "STARTTLS"));
            assertFalse(cfg.toString().contains("supersecret"));
        }
    }
}
