package org.ssd.project.accounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ssd.project.email.IEmailService;

/** Tests the email-verification registration flow with a fake mailbox and a controllable clock. */
class AuthServiceVerificationTest {

    private static final String EMAIL = "bob@example.com";

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class Mailbox implements IEmailService {
        final List<String> codes = new ArrayList<>();

        @Override
        public void sendWelcomeEmail(User user) {}

        @Override
        public void sendVerificationEmail(String username, String email, String code) {
            codes.add(code);
        }

        String last() {
            return codes.get(codes.size() - 1);
        }
    }

    private static final class FakePasswords implements IPasswordService {
        @Override
        public String hash(String raw) {
            return "h:" + raw;
        }

        @Override
        public boolean verify(String raw, String hash) {
            return hash.equals("h:" + raw);
        }
    }

    private static final class Repo implements IUserRepository {
        final Map<String, User> byEmail = new TreeMap<>();
        long next = 1;

        @Override
        public Optional<User> create(String username, String email, String hash) {
            if (byEmail.containsKey(email)) return Optional.empty();
            User u = new User(next++, username, email, hash);
            byEmail.put(email, u);
            return Optional.of(u);
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.ofNullable(byEmail.get(email));
        }

        @Override
        public Optional<User> findById(long id) {
            return byEmail.values().stream().filter(u -> u.id() == id).findFirst();
        }

        @Override
        public Optional<User> updateUsername(long id, String n) {
            return Optional.empty();
        }

        @Override
        public Optional<User> updatePasswordHash(long id, String h) {
            return Optional.empty();
        }

        @Override
        public boolean deleteById(long id) {
            return false;
        }
    }

    private Repo repo;
    private Mailbox mailbox;
    private MutableClock clock;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        repo = new Repo();
        mailbox = new Mailbox();
        clock = new MutableClock();
        auth = new AuthService(repo, new FakePasswords(), mailbox, clock);
    }

    @Test
    void startThenCompleteCreatesAccount() {
        assertEquals(AuthService.StartResult.CODE_SENT, auth.startRegistration("bob", EMAIL, "pw"));
        AuthService.VerifyOutcome out = auth.completeRegistration(EMAIL, mailbox.last());
        assertEquals(AuthService.VerifyResult.OK, out.result());
        assertNotNull(out.user());
        assertTrue(repo.findByEmail(EMAIL).isPresent());
    }

    @Test
    void noAccountExistsBeforeVerification() {
        auth.startRegistration("bob", EMAIL, "pw");
        assertTrue(repo.findByEmail(EMAIL).isEmpty());
    }

    @Test
    void startAfterExistingAccountIsEmailTaken() {
        // Seed the repo with an already-verified account, then confirm startRegistration rejects it
        repo.create("bob", EMAIL, "h:pw");
        assertEquals(
                AuthService.StartResult.EMAIL_TAKEN, auth.startRegistration("bob", EMAIL, "pw"));
        assertTrue(mailbox.codes.isEmpty());
    }

    @Test
    void wrongCodeIsRejectedAndAccountNotCreated() {
        auth.startRegistration("bob", EMAIL, "pw");
        String wrong = mailbox.last().equals("000000") ? "111111" : "000000";
        AuthService.VerifyOutcome out = auth.completeRegistration(EMAIL, wrong);
        assertEquals(AuthService.VerifyResult.WRONG_CODE, out.result());
        assertNull(out.user());
        assertTrue(repo.findByEmail(EMAIL).isEmpty());
    }

    @Test
    void codeExpiresAfterTenMinutes() {
        auth.startRegistration("bob", EMAIL, "pw");
        clock.advance(Duration.ofMinutes(11));
        assertEquals(
                AuthService.VerifyResult.EXPIRED,
                auth.completeRegistration(EMAIL, mailbox.last()).result());
    }

    @Test
    void tooManyWrongAttemptsLocksOutEvenTheRightCode() {
        auth.startRegistration("bob", EMAIL, "pw");
        String right = mailbox.last();
        String wrong = right.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) auth.completeRegistration(EMAIL, wrong);
        assertEquals(
                AuthService.VerifyResult.TOO_MANY_ATTEMPTS,
                auth.completeRegistration(EMAIL, right).result());
    }

    @Test
    void resendIsBlockedDuringCooldownThenAllowed() {
        auth.startRegistration("bob", EMAIL, "pw");
        assertEquals(AuthService.StartResult.TOO_SOON, auth.resendCode(EMAIL));
        clock.advance(Duration.ofSeconds(61));
        assertEquals(AuthService.StartResult.CODE_SENT, auth.resendCode(EMAIL));
        assertEquals(2, mailbox.codes.size());
    }

    @Test
    void resendWithoutPendingRegistration() {
        assertEquals(AuthService.StartResult.NO_PENDING, auth.resendCode(EMAIL));
    }

    @Test
    void codeCannotBeReusedAfterSuccess() {
        auth.startRegistration("bob", EMAIL, "pw");
        String code = mailbox.last();
        auth.completeRegistration(EMAIL, code);
        assertEquals(
                AuthService.VerifyResult.NO_PENDING,
                auth.completeRegistration(EMAIL, code).result());
    }
}
