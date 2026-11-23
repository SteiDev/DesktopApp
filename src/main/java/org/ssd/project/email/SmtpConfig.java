package org.ssd.project.email;

import java.util.Locale;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * SMTP connection settings. Credentials come from the environment, never from source code or git.
 *
 * <p>Environment variables: {@code SMTP_HOST}, {@code SMTP_PORT} (default 587), {@code
 * SMTP_USERNAME}, {@code SMTP_PASSWORD}, {@code SMTP_FROM}, {@code SMTP_SECURITY} ({@code STARTTLS}
 * (default), {@code SSL} or {@code NONE}).
 */
public record SmtpConfig(
        String host,
        int port,
        String username,
        String password,
        String from,
        String fromName,
        Security security) {

    public enum Security {
        /** Plain connection upgraded to TLS (usually port 587). */
        STARTTLS,
        /** TLS from the first byte (usually port 465). */
        SSL,
        /** No encryption. Only for a local test server. */
        NONE
    }

    public SmtpConfig {
        requireNonBlank(host, "host");
        requireNonBlank(from, "from");
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        Objects.requireNonNull(security, "security");
    }

    public SmtpConfig(
            String host,
            int port,
            String username,
            String password,
            String from,
            Security security) {
        this(host, port, username, password, from, null, security);
    }

    public static SmtpConfig fromEnvironment() {
        return fromEnvironment(System::getenv);
    }

    /* The lookup is a parameter so tests do not depend on real environment variables */
    public static SmtpConfig fromEnvironment(UnaryOperator<String> env) {
        String port = orDefault(env.apply("SMTP_PORT"), "587");
        String security = orDefault(env.apply("SMTP_SECURITY"), "STARTTLS");
        return new SmtpConfig(
                env.apply("SMTP_HOST"),
                Integer.parseInt(port.trim()),
                env.apply("SMTP_USERNAME"),
                env.apply("SMTP_PASSWORD"),
                env.apply("SMTP_FROM"),
                env.apply("SMTP_FROM_NAME"),
                Security.valueOf(security.trim().toUpperCase(Locale.ROOT)));
    }

    boolean hasCredentials() {
        return username != null && !username.isBlank();
    }

    /* Keeps the password out of logs and stack traces */
    @Override
    public String toString() {
        return "SmtpConfig[host=%s, port=%d, username=%s, from=%s, security=%s]"
                .formatted(host, port, username, from, security);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SMTP " + name + " must not be null or blank");
        }
    }
}
