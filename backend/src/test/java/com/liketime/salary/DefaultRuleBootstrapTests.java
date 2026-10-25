package com.liketime.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultRuleBootstrapTests {

    private final SalaryRuleRepository rules = mock(SalaryRuleRepository.class);

    @Test
    void createsTheDefaultRateWhenThereAreNoRules() {
        when(rules.count()).thenReturn(0L);

        new DefaultRuleBootstrap(rules, "100", LocalDate.of(2026, 1, 1)).run(null);

        ArgumentCaptor<SalaryRule> saved = ArgumentCaptor.forClass(SalaryRule.class);
        verify(rules).save(saved.capture());
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(saved.getValue().getTeacherId()).isNull();
        assertThat(saved.getValue().getGroupId()).isNull();
        assertThat(saved.getValue().getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(saved.getValue().getEffectiveTo()).isNull();
    }

    @Test
    void doesNothingWhenRulesAlreadyExist() {
        when(rules.count()).thenReturn(3L);

        new DefaultRuleBootstrap(rules, "100", LocalDate.of(2026, 1, 1)).run(null);

        verify(rules, never()).save(any());
    }

    @Test
    void doesNothingWhenNoRateIsConfigured() {
        when(rules.count()).thenReturn(0L);

        new DefaultRuleBootstrap(rules, "", LocalDate.of(2026, 1, 1)).run(null);

        verify(rules, never()).save(any());
    }
}
