package com.liketime;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LikeTimeApplicationTests {

    @Test
    void contextLoads() {
        // Проверяет, что приложение поднимается и Flyway подключается к liketime_test
    }
}
