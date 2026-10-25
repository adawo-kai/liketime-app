package com.liketime.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки безопасности. jwtSecret обязателен и не имеет значения по умолчанию:
 * без него приложение не стартует.
 *
 * @param loginMaxAttempts сколько неудачных входов подряд допускается для пары «логин + адрес»
 * @param loginLockout     на сколько блокируется вход после превышения лимита (и окно подсчёта неудач)
 */
@Validated
@ConfigurationProperties(prefix = "liketime.security")
public record LikeTimeSecurityProperties(
        @NotBlank @Size(min = 32) String jwtSecret,
        @DefaultValue("8h") Duration accessTokenTtl,
        @DefaultValue("5") @Min(1) int loginMaxAttempts,
        @DefaultValue("15m") Duration loginLockout) {
}
