package com.liketime.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Единые часы приложения (UTC). В тестах подменяются, чтобы проверять время без ожидания. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
