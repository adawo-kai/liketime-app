package com.liketime.salary;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Создаёт стандартную ставку при первом запуске, если в базе нет ни одного правила и ставка задана в
 * конфигурации (liketime.bootstrap.default-visit-rate). Нужна, чтобы расчёт работал сразу после установки;
 * дальше ставки меняются в интерфейсе.
 */
@Component
public class DefaultRuleBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultRuleBootstrap.class);

    private final SalaryRuleRepository rules;
    private final BigDecimal rate;
    private final LocalDate from;

    public DefaultRuleBootstrap(SalaryRuleRepository rules,
                                @Value("${liketime.bootstrap.default-visit-rate:}") String rate,
                                @Value("${liketime.bootstrap.default-visit-rate-from:2026-01-01}") LocalDate from) {
        this.rules = rules;
        this.rate = rate == null || rate.isBlank() ? null : new BigDecimal(rate.trim());
        this.from = from;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (rate == null || rules.count() > 0) {
            return;
        }
        rules.save(new SalaryRule(null, null, rate.setScale(2, java.math.RoundingMode.HALF_UP), from, null,
                "Стандартная ставка (создана при первом запуске)"));
        log.info("Создана стандартная ставка {} за приход с {}", rate, from);
    }
}
