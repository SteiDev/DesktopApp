package org.ssd.project.email;

import org.ssd.project.accounts.User;

/** Sends account-related emails. */
public interface IEmailService {
    /**
     * Sends the "your account was created" email.
     *
     * <p>Implementations must not throw: a failed email must never undo a registration, so failures
     * are logged instead.
     */
    void sendWelcomeEmail(User user);

    void sendVerificationEmail(String username, String email, String code);

    /** Returns a no-op implementation used in tests or when email is disabled. */
    static IEmailService noOp() {
        return new IEmailService() {
            @Override
            public void sendWelcomeEmail(User user) {}

            @Override
            public void sendVerificationEmail(String username, String email, String code) {}
        };
    }
}
