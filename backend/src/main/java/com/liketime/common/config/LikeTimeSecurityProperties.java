package com.liketime.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Настройки безопасности. jwtSecret обязателен и не имеет значения по умолчанию:
 * без него приложение не стартует.
 */
@Validated
@ConfigurationProperties(prefix = "liketime.security")
public record LikeTimeSecurityProperties(
        @NotBlank @Size(min = 32) String jwtSecret,
        @DefaultValue("8h") Duration accessTokenTtl) {
}
