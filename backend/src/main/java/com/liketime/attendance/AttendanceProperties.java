package com.liketime.attendance;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;

/**
 * Правила посещаемости.
 *
 * @param earlyMinutes     за сколько минут до начала занятия уже можно отмечать приход
 * @param lateAfterMinutes через сколько минут после начала приход считается опозданием
 * @param teacherEditDays  сколько дней после занятия преподаватель может сам править отметки
 */
@ConfigurationProperties(prefix = "liketime.attendance")
public record AttendanceProperties(
        @DefaultValue("30") int earlyMinutes,
        @DefaultValue("10") int lateAfterMinutes,
        @DefaultValue("3") int teacherEditDays) {

    @Configuration
    @EnableConfigurationProperties(AttendanceProperties.class)
    static class Registration {
    }
}
