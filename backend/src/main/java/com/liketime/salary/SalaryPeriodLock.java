package com.liketime.salary;

import com.liketime.common.PeriodLock;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Даты утверждённых и выплаченных периодов закрыты для правок: иначе поплывёт уже согласованная зарплата. */
@Component
public class SalaryPeriodLock implements PeriodLock {

    private final SalaryPeriodRepository periods;

    public SalaryPeriodLock(SalaryPeriodRepository periods) {
        this.periods = periods;
    }

    @Override
    public void assertEditable(LocalDate date) {
        if (isLocked(date)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Зарплатный период за " + date + " утверждён. Верните его в расчёт, чтобы внести изменения");
        }
    }

    @Override
    public boolean isLocked(LocalDate date) {
        return periods.existsLockedOn(date);
    }
}
