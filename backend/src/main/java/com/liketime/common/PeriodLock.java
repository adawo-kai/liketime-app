package com.liketime.common;

import java.time.LocalDate;

/**
 * Запрещает менять данные, по которым зарплата уже утверждена. Реализация лежит в модуле зарплаты;
 * интерфейс вынесен сюда, чтобы посещаемость не зависела от зарплаты напрямую.
 */
public interface PeriodLock {

    /** Бросает 409, если дата попадает в утверждённый или выплаченный зарплатный период */
    void assertEditable(LocalDate date);

    boolean isLocked(LocalDate date);
}
