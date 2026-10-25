package com.liketime.auth;

import com.liketime.common.config.LikeTimeSecurityProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Защита от подбора пароля. Неудачные попытки считаются по трём ключам:
 * <ul>
 *   <li>пара «логин + адрес»: основной лимит (по умолчанию 5), блокирует именно этого человека;</li>
 *   <li>только логин: лимит в 4 раза выше, ловит перебор с разных адресов, но не даёт одному
 *       злоумышленнику легко заблокировать чужую учётную запись целиком;</li>
 *   <li>только адрес: лимит в 6 раз выше, ловит перебор разных логинов с одного адреса.</li>
 * </ul>
 * Блокировка действует и на верный пароль. Счётчики хранятся в памяти: после перезапуска сбрасываются.
 */
@Component
public class LoginAttemptLimiter {

    private static final int PURGE_THRESHOLD = 10_000;

    private final int pairLimit;
    private final int loginLimit;
    private final int ipLimit;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(LikeTimeSecurityProperties properties, Clock clock) {
        this.pairLimit = properties.loginMaxAttempts();
        this.loginLimit = properties.loginMaxAttempts() * 4;
        this.ipLimit = properties.loginMaxAttempts() * 6;
        this.window = properties.loginLockout();
        this.clock = clock;
    }

    /** Бросает 429, если вход для этого логина или адреса сейчас заблокирован */
    public void assertAllowed(String login, String ip) {
        Instant now = clock.instant();
        Duration remaining = Duration.ZERO;
        for (String key : keys(login, ip)) {
            State state = states.get(key);
            if (state != null) {
                Duration left = state.lockRemaining(now);
                if (left.compareTo(remaining) > 0) {
                    remaining = left;
                }
            }
        }
        if (!remaining.isZero()) {
            long minutes = Math.max(1, (remaining.toSeconds() + 59) / 60);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Слишком много неудачных попыток входа. Повторите через " + minutes + " мин.");
        }
    }

    public void recordFailure(String login, String ip) {
        Instant now = clock.instant();
        purgeIfLarge(now);
        count("pair:" + normalize(login) + "|" + ip, pairLimit, now);
        count("login:" + normalize(login), loginLimit, now);
        count("ip:" + ip, ipLimit, now);
    }

    /** После успешного входа счётчики этого человека обнуляются (счётчик адреса остаётся) */
    public void recordSuccess(String login, String ip) {
        states.remove("pair:" + normalize(login) + "|" + ip);
        states.remove("login:" + normalize(login));
    }

    private void count(String key, int limit, Instant now) {
        states.computeIfAbsent(key, k -> new State()).fail(now, window, limit);
    }

    private String[] keys(String login, String ip) {
        return new String[] {"pair:" + normalize(login) + "|" + ip, "login:" + normalize(login), "ip:" + ip};
    }

    private static String normalize(String login) {
        return login == null ? "" : login.trim().toLowerCase(Locale.ROOT);
    }

    /** Чтобы память не росла бесконечно при переборе случайных логинов, устаревшие записи вычищаются */
    private void purgeIfLarge(Instant now) {
        if (states.size() > PURGE_THRESHOLD) {
            states.values().removeIf(s -> s.isExpired(now, window));
        }
    }

    private static final class State {
        private int failures;
        private Instant windowStart;
        private Instant lockedUntil;

        synchronized void fail(Instant now, Duration window, int limit) {
            if (windowStart == null || now.isAfter(windowStart.plus(window))) {
                failures = 0;
                windowStart = now;
            }
            failures++;
            if (failures >= limit) {
                lockedUntil = now.plus(window);
            }
        }

        synchronized Duration lockRemaining(Instant now) {
            if (lockedUntil == null || !now.isBefore(lockedUntil)) {
                return Duration.ZERO;
            }
            return Duration.between(now, lockedUntil);
        }

        synchronized boolean isExpired(Instant now, Duration window) {
            Instant last = lockedUntil != null ? lockedUntil : (windowStart != null ? windowStart.plus(window) : null);
            return last == null || !now.isBefore(last);
        }
    }
}
