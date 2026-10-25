package com.liketime.salary;

public enum PeriodStatus {
    /** Период создан, расчёта ещё не было */
    OPEN,
    /** Рассчитан; данные ещё можно менять и пересчитывать */
    CALCULATED,
    /** Утверждён: посещаемость и занятия периода заблокированы */
    APPROVED,
    /** Выплачен */
    PAID;

    public boolean isLocked() {
        return this == APPROVED || this == PAID;
    }
}
