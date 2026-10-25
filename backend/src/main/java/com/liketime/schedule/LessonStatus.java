package com.liketime.schedule;

public enum LessonStatus {
    /** Запланировано */
    PLANNED,
    /** Проведено: по таким занятиям считается зарплата */
    HELD,
    /** Отменено: посещаемость не ведётся */
    CANCELLED
}
