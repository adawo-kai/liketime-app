package com.liketime.auth;

import com.liketime.audit.AuditService;
import com.liketime.common.config.LikeTimeSecurityProperties;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final LikeTimeSecurityProperties properties;
    /** Хеш для несуществующих логинов: время ответа не выдаёт, существует пользователь или нет */
    private final String dummyHash;

    private final LoginAttemptLimiter limiter;
    private final AuditService audit;

    public AuthService(UserAccountRepository users, PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder, LikeTimeSecurityProperties properties, LoginAttemptLimiter limiter,
                       AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.limiter = limiter;
        this.audit = audit;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Вход по логину и паролю. Перед проверкой пароля смотрим, не заблокирован ли вход после серии
     * неудач: заблокированному клиенту отвечаем 429, даже если пароль верный.
     */
    public TokenResponse login(String login, String rawPassword, String clientIp) {
        limiter.assertAllowed(login, clientIp);
        UserAccount user = users.findByLogin(login).orElse(null);
        if (user == null) {
            passwordEncoder.matches(rawPassword, dummyHash);
            limiter.recordFailure(login, clientIp);
            throw invalidCredentials();
        }
        boolean passwordMatches = passwordEncoder.matches(rawPassword, user.getPasswordHash());
        if (!passwordMatches || !user.isEnabled()) {
            limiter.recordFailure(login, clientIp);
            throw invalidCredentials();
        }
        limiter.recordSuccess(login, clientIp);
        return issueToken(user);
    }

    /**
     * Смена собственного пароля. Нужен текущий пароль: так украденный токен не позволит угнать учётную запись.
     * Неверный текущий пароль считается как неудачная попытка входа (та же блокировка после серии ошибок).
     * Все ранее выданные токены перестают действовать; в ответе приходит новый, чтобы текущий сеанс продолжился.
     * Ошибку неверного пароля отдаём как 400, а не 401: 401 сайт трактует как «сеанс закончился» и выходит из системы.
     */
    @Transactional
    public TokenResponse changePassword(String login, String currentPassword, String newPassword, String clientIp) {
        limiter.assertAllowed(login, clientIp);
        UserAccount user = users.findByLogin(login)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            limiter.recordFailure(login, clientIp);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Текущий пароль указан неверно");
        }
        PasswordPolicy.check(newPassword);
        if (newPassword.equals(currentPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Новый пароль должен отличаться от текущего");
        }
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        limiter.recordSuccess(login, clientIp);
        // Сам пароль в журнал не пишется, только факт смены
        audit.record(login, "USER_PASSWORD_CHANGE", "user", user.getId(), Map.of("login", login));
        return issueToken(user);
    }

    private TokenResponse issueToken(UserAccount user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getLogin())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("role", user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.accessTokenTtl().toSeconds());
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль");
    }
}
