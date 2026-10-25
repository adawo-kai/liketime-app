package com.liketime.device;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;

/**
 * Настройки устройств.
 *
 * @param minConfidence          минимальная уверенность распознавания (0..1); ниже события не засчитываются
 * @param futureToleranceMinutes насколько вперёд может «убежать» часы устройства, минут
 * @param secrets                секреты устройств по имени из поля secretRef; значения задаются только в
 *                               локальной конфигурации и никогда не попадают в базу или репозиторий
 */
@ConfigurationProperties(prefix = "liketime.devices")
public record DeviceProperties(
        @DefaultValue("0.8") double minConfidence,
        @DefaultValue("10") int futureToleranceMinutes,
        @DefaultValue Map<String, String> secrets) {

    @Configuration
    @EnableConfigurationProperties(DeviceProperties.class)
    static class Registration {
    }
}
