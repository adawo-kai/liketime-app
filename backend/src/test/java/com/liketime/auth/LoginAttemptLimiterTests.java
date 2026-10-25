package com.liketime.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liketime.MutableClock;
import com.liketime.common.config.LikeTimeSecurityProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Чистые тесты ограничителя на управляемых часах: ждать 15 минут по-настоящему не нужно. */
class LoginAttemptLimiterTests {

    private static final String IP = "10.0.0.1";

    private MutableClock clock;
    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-25T10:00:00Z"));
        limiter = new LoginAttemptLimiter(
                new LikeTimeSecurityProperties("x".repeat(40), Duration.ofHours(8), 5, Duration.ofMinutes(15)), clock);
    }

    private void fail(String login, String ip, int times) {
        for (int i = 0; i < times; i++) {
            limiter.assertAllowed(login, ip);
            limiter.recordFailure(login, ip);
        }
    }

    @Test
    void fiveFailuresLockTheLoginFromThatAddress() {
        fail("ainura", IP, 5);

        assertThatThrownBy(() -> limiter.assertAllowed("ainura", IP))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS))
                .hasMessageContaining("15 мин");
    }

    @Test
    void fourFailuresDoNotLock() {
        fail("ainura", IP, 4);

        assertThatCode(() -> limiter.assertAllowed("ainura", IP)).doesNotThrowAnyException();
    }

    @Test
    void lockDoesNotAffectOtherAddressesOrOtherLogins() {
        fail("ainura", IP, 5);

        assertThatCode(() -> limiter.assertAllowed("ainura", "10.0.0.2")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.assertAllowed("bakyt", IP)).doesNotThrowAnyException();
    }

    @Test
    void lockExpiresAfterTheWindow() {
        fail("ainura", IP, 5);
        clock.advance(Duration.ofMinutes(14));
        assertThatThrownBy(() -> limiter.assertAllowed("ainura", IP)).hasMessageContaining("1 мин");

        clock.advance(Duration.ofMinutes(2));

        assertThatCode(() -> limiter.assertAllowed("ainura", IP)).doesNotThrowAnyException();
    }

    @Test
    void failuresOlderThanTheWindowAreForgotten() {
        fail("ainura", IP, 4);
        clock.advance(Duration.ofMinutes(16));
        fail("ainura", IP, 4);

        assertThatCode(() -> limiter.assertAllowed("ainura", IP)).doesNotThrowAnyException();
    }

    @Test
    void successfulLoginResetsTheCounter() {
        fail("ainura", IP, 4);
        limiter.recordSuccess("ainura", IP);
        fail("ainura", IP, 4);

        assertThatCode(() -> limiter.assertAllowed("ainura", IP)).doesNotThrowAnyException();
    }

    @Test
    void guessingOneLoginFromManyAddressesIsStoppedEventually() {
        // 20 неудач (лимит логина = 4 * 5) с разных адресов блокируют перебор этого логина везде
        for (int i = 0; i < 20; i++) {
            String ip = "10.1.0." + i;
            limiter.assertAllowed("ainura", ip);
            limiter.recordFailure("ainura", ip);
        }

        assertThatThrownBy(() -> limiter.assertAllowed("ainura", "10.1.1.1"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void guessingManyLoginsFromOneAddressIsStoppedEventually() {
        // 30 неудач (лимит адреса = 6 * 5) с одного адреса по разным логинам блокируют адрес
        for (int i = 0; i < 30; i++) {
            limiter.assertAllowed("user" + i, IP);
            limiter.recordFailure("user" + i, IP);
        }

        assertThatThrownBy(() -> limiter.assertAllowed("somebody-else", IP))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void loginIsMatchedCaseInsensitively() {
        fail("Ainura", IP, 5);

        assertThatThrownBy(() -> limiter.assertAllowed("AINURA", IP)).isInstanceOf(ResponseStatusException.class);
    }
}
