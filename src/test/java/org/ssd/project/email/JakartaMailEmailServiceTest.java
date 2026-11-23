package org.ssd.project.email;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.ssd.project.accounts.User;

/**
 * Tests for {@link JakartaMailEmailService} message-building logic.
 *
 * <p>Both {@code buildWelcomeMessage} and {@code buildVerificationMessage} are package-private
 * specifically so tests can inspect the resulting {@link MimeMessage} without needing a live SMTP
 * server. No network connection is made.
 */
class JakartaMailEmailServiceTest {

    private static final String HOST = "localhost";
    private static final int PORT = 25;
    private static final String FROM = "noreply@example.com";
    private static final String FROM_NAME = "Test App";

    private JakartaMailEmailService service;

    @BeforeEach
    void setUp() {
        SmtpConfig config =
                new SmtpConfig(HOST, PORT, null, null, FROM, FROM_NAME, SmtpConfig.Security.NONE);
        service = new JakartaMailEmailService(config);
    }

    /* ------------------------------------------------------------------ welcome email */

    @Nested
    @DisplayName("buildWelcomeMessage")
    class WelcomeMessage {

        @Test
        @DisplayName("sets the recipient to the user's email")
        void recipient_isUserEmail() throws MessagingException {
            User user = new User(1L, "alice", "alice@example.com", "hash");
            MimeMessage msg = service.buildWelcomeMessage(user);

            Address[] to = msg.getRecipients(Message.RecipientType.TO);
            assertNotNull(to);
            assertEquals(1, to.length);
            assertEquals("alice@example.com", to[0].toString());
        }

        @Test
        @DisplayName("subject contains the word 'Welcome'")
        void subject_containsWelcome() throws MessagingException {
            User user = new User(2L, "bob", "bob@example.com", "hash");
            MimeMessage msg = service.buildWelcomeMessage(user);

            assertTrue(
                    msg.getSubject().toLowerCase().contains("welcome"),
                    "Expected subject to contain 'welcome' but was: " + msg.getSubject());
        }

        @Test
        @DisplayName("body addresses the user by their username")
        void body_containsUsername() throws Exception {
            User user = new User(3L, "charlie", "charlie@example.com", "hash");
            MimeMessage msg = service.buildWelcomeMessage(user);

            String body = (String) msg.getContent();
            assertTrue(
                    body.contains("charlie"),
                    "Expected body to contain username 'charlie' but was: " + body);
        }

        @Test
        @DisplayName("from address matches the configured sender")
        void from_matchesConfig() throws MessagingException {
            User user = new User(4L, "dave", "dave@example.com", "hash");
            MimeMessage msg = service.buildWelcomeMessage(user);

            Address[] from = msg.getFrom();
            assertNotNull(from);
            assertEquals(1, from.length);
            assertTrue(
                    from[0].toString().contains(FROM),
                    "Expected From to contain '" + FROM + "' but was: " + from[0]);
        }

        @Test
        @DisplayName("from personal name is set when configured")
        void from_hasPersonalName() throws MessagingException {
            User user = new User(5L, "eve", "eve@example.com", "hash");
            MimeMessage msg = service.buildWelcomeMessage(user);

            String from = msg.getFrom()[0].toString();
            assertTrue(
                    from.contains(FROM_NAME),
                    "Expected From to include personal name '" + FROM_NAME + "' but was: " + from);
        }

        @Test
        @DisplayName("sent date is set")
        void sentDate_isSet() throws MessagingException {
            User user = new User(6L, "frank", "frank@example.com", "hash");
            MimeMessage msg = service.buildWelcomeMessage(user);

            assertNotNull(msg.getSentDate());
        }

        @Test
        @DisplayName("rejects a malformed recipient address")
        void malformedEmail_throws() {
            User user = new User(7L, "ghost", "@@bad", "hash");

            assertThrows(MessagingException.class, () -> service.buildWelcomeMessage(user));
        }
    }

    /* ------------------------------------------------------------------ verification email */

    @Nested
    @DisplayName("buildVerificationMessage")
    class VerificationMessage {

        @Test
        @DisplayName("sets the recipient to the given email")
        void recipient_isGivenEmail() throws MessagingException {
            MimeMessage msg =
                    service.buildVerificationMessage("alice", "alice@example.com", "123456");

            Address[] to = msg.getRecipients(Message.RecipientType.TO);
            assertNotNull(to);
            assertEquals(1, to.length);
            assertEquals("alice@example.com", to[0].toString());
        }

        @Test
        @DisplayName("subject mentions verification")
        void subject_mentionsVerification() throws MessagingException {
            MimeMessage msg =
                    service.buildVerificationMessage("alice", "alice@example.com", "123456");

            assertTrue(
                    msg.getSubject().toLowerCase().contains("verif"),
                    "Expected subject to contain 'verif' but was: " + msg.getSubject());
        }

        @Test
        @DisplayName("body contains the verification code")
        void body_containsCode() throws Exception {
            MimeMessage msg =
                    service.buildVerificationMessage("alice", "alice@example.com", "987654");

            String body = (String) msg.getContent();
            assertTrue(
                    body.contains("987654"),
                    "Expected body to contain code '987654' but was: " + body);
        }

        @Test
        @DisplayName("body addresses the user by username")
        void body_containsUsername() throws Exception {
            MimeMessage msg = service.buildVerificationMessage("bob", "bob@example.com", "000001");

            String body = (String) msg.getContent();
            assertTrue(body.contains("bob"), "Expected body to contain 'bob' but was: " + body);
        }

        @Test
        @DisplayName("body mentions the 10-minute expiry")
        void body_mentionsExpiry() throws Exception {
            MimeMessage msg =
                    service.buildVerificationMessage("alice", "alice@example.com", "111111");

            String body = (String) msg.getContent();
            assertTrue(
                    body.contains("10"), "Expected body to mention expiry time but was: " + body);
        }

        @Test
        @DisplayName("from address matches the configured sender")
        void from_matchesConfig() throws MessagingException {
            MimeMessage msg =
                    service.buildVerificationMessage("alice", "alice@example.com", "123456");

            String from = msg.getFrom()[0].toString();
            assertTrue(
                    from.contains(FROM),
                    "Expected From to contain '" + FROM + "' but was: " + from);
        }

        @Test
        @DisplayName("sent date is set")
        void sentDate_isSet() throws MessagingException {
            MimeMessage msg =
                    service.buildVerificationMessage("alice", "alice@example.com", "123456");

            assertNotNull(msg.getSentDate());
        }

        @Test
        @DisplayName("all fields are consistent for a typical call")
        void allFields_consistent() throws Exception {
            MimeMessage msg =
                    service.buildVerificationMessage("carol", "carol@example.com", "555555");

            assertAll(
                    () ->
                            assertEquals(
                                    "carol@example.com",
                                    msg.getRecipients(Message.RecipientType.TO)[0].toString()),
                    () -> assertTrue(msg.getSubject().toLowerCase().contains("verif")),
                    () -> assertTrue(((String) msg.getContent()).contains("555555")),
                    () -> assertTrue(((String) msg.getContent()).contains("carol")),
                    () -> assertNotNull(msg.getSentDate()));
        }

        @Test
        @DisplayName("rejects a malformed recipient address")
        void malformedEmail_throws() {
            assertThrows(
                    MessagingException.class,
                    () -> service.buildVerificationMessage("x", "@@bad", "000000"));
        }
    }

    /* ------------------------------------------------------------------ no-personal-name variant */

    @Test
    @DisplayName("works without a fromName configured")
    void noFromName_fromAddressStillSet() throws MessagingException {
        SmtpConfig cfg = new SmtpConfig(HOST, PORT, null, null, FROM, SmtpConfig.Security.NONE);
        JakartaMailEmailService svc = new JakartaMailEmailService(cfg);

        MimeMessage msg = svc.buildWelcomeMessage(new User(1L, "z", "z@example.com", "h"));
        assertTrue(msg.getFrom()[0].toString().contains(FROM));
    }
}
