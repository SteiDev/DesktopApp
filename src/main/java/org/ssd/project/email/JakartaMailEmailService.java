package org.ssd.project.email;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ssd.project.accounts.User;

/**
 * Sends emails over SMTP with Jakarta Mail. Sending happens on a background thread so that
 * registration does not wait for the mail server. Call {@link #close()} on shutdown to let queued
 * emails go out.
 */
public final class JakartaMailEmailService implements IEmailService, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(JakartaMailEmailService.class);

    private static final String WELCOME_SUBJECT = "Welcome! Your account has been created";
    private static final String VERIFICATION_SUBJECT = "Your verification code";
    private static final int TIMEOUT_MILLIS = 10_000;
    private static final int SHUTDOWN_WAIT_SECONDS = 10;

    private final SmtpConfig config;
    private final Session session;
    private final ExecutorService executor;

    public JakartaMailEmailService(SmtpConfig config) {
        this.config = config;
        this.session = Session.getInstance(sessionProperties(config), authenticator(config));
        this.executor =
                Executors.newSingleThreadExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "email-sender");
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    private static Properties sessionProperties(SmtpConfig config) {
        Properties props = new Properties();
        props.put("mail.smtp.host", config.host());
        props.put("mail.smtp.port", String.valueOf(config.port()));
        props.put("mail.smtp.auth", String.valueOf(config.hasCredentials()));

        /* A hung mail server must not block the sender thread forever */
        props.put("mail.smtp.connectiontimeout", String.valueOf(TIMEOUT_MILLIS));
        props.put("mail.smtp.timeout", String.valueOf(TIMEOUT_MILLIS));
        props.put("mail.smtp.writetimeout", String.valueOf(TIMEOUT_MILLIS));

        switch (config.security()) {
            case STARTTLS -> {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case SSL -> {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.checkserveridentity", "true");
            }
            case NONE -> {
                /* Plain SMTP, for a local test server only */
            }
        }
        return props;
    }

    private static Authenticator authenticator(SmtpConfig config) {
        if (!config.hasCredentials()) {
            return null;
        }
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(config.username(), config.password());
            }
        };
    }

    /* ------------------------------------------------------------------ welcome email */

    @Override
    public void sendWelcomeEmail(User user) {
        try {
            executor.execute(() -> sendWelcome(user));
        } catch (RejectedExecutionException e) {
            log.error("Welcome email for user id={} not sent: service is shut down", user.id());
        }
    }

    private void sendWelcome(User user) {
        try {
            Transport.send(buildWelcomeMessage(user));
            log.info("Welcome email sent for user id={}", user.id());
        } catch (MessagingException | RuntimeException e) {
            /* Logs the id only, never the address */
            log.error("Could not send welcome email for user id={}: {}", user.id(), e.toString());
        }
    }

    /* Package-private so tests can check the message without an SMTP server */
    MimeMessage buildWelcomeMessage(User user) throws MessagingException {
        MimeMessage message = new MimeMessage(session);
        message.setFrom(senderAddress());
        /* Strict parsing rejects malformed addresses, including ones with injected headers */
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(user.email(), true));
        message.setSubject(WELCOME_SUBJECT, "UTF-8");
        message.setText(welcomeBody(user.username()), "UTF-8");
        message.setSentDate(new Date());
        return message;
    }

    private static String welcomeBody(String username) {
        return "Hi %s,%n%nYour account has been created successfully. ".formatted(username)
                + "You can now log in with this email address.%n%n".formatted()
                + "If you did not create this account, you can ignore this message.%n".formatted();
    }

    /* ------------------------------------------------------------------ verification email */

    @Override
    public void sendVerificationEmail(String username, String email, String code) {
        try {
            executor.execute(() -> sendVerification(username, email, code));
        } catch (RejectedExecutionException e) {
            log.error("Verification email not sent: service is shut down");
        }
    }

    private void sendVerification(String username, String email, String code) {
        try {
            Transport.send(buildVerificationMessage(username, email, code));
            log.info("Verification email sent");
        } catch (MessagingException | RuntimeException e) {
            log.error("Could not send verification email: {}", e.toString());
        }
    }

    /* Package-private so tests can check the message without an SMTP server */
    MimeMessage buildVerificationMessage(String username, String email, String code)
            throws MessagingException {
        MimeMessage message = new MimeMessage(session);
        message.setFrom(senderAddress());
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email, true));
        message.setSubject(VERIFICATION_SUBJECT, "UTF-8");
        message.setText(verificationBody(username, code), "UTF-8");
        message.setSentDate(new Date());
        return message;
    }

    private static String verificationBody(String username, String code) {
        return ("Hi %s,%n%nYour verification code is: %s%n%n"
                        + "It expires in 10 minutes. If you did not request this,"
                        + " you can safely ignore this message.%n")
                .formatted(username, code);
    }

    /* ------------------------------------------------------------------ lifecycle */

    /** Lets queued emails finish, then stops the sender thread. */
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Email service stopped with emails still queued");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private InternetAddress senderAddress() throws MessagingException {
        InternetAddress sender = new InternetAddress(config.from(), true);
        if (config.fromName() != null && !config.fromName().isBlank()) {
            try {
                sender.setPersonal(config.fromName(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new MessagingException("UTF-8 is not supported", e);
            }
        }
        return sender;
    }
}
