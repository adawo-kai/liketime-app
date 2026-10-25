package com.liketime.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Работа со временем. В базе всё хранится в UTC, а расписание, даты занятий и зарплатные периоды
 * считаются по местному времени школы (по умолчанию Asia/Bishkek, UTC+6, без перехода на летнее время).
 */
@Component
public class AppTime {

    private final ZoneId zone;
    private final Clock clock;

    public AppTime(@Value("${liketime.timezone:Asia/Bishkek}") String zoneId, Clock clock) {
        this.zone = ZoneId.of(zoneId);
        this.clock = clock.withZone(ZoneOffset.UTC);
    }

    public ZoneId zone() {
        return zone;
    }

    /** Текущий момент в UTC, в таком виде он хранится в базе */
    public LocalDateTime nowUtc() {
        return LocalDateTime.now(clock);
    }

    /** Сегодняшняя дата по местному времени школы */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    /** Местная дата и время -> UTC для хранения */
    public LocalDateTime toUtc(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(zone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** Начало местных суток в UTC */
    public LocalDateTime startOfDayUtc(LocalDate date) {
        return toUtc(date, LocalTime.MIDNIGHT);
    }

    private ZonedDateTime toLocal(LocalDateTime utc) {
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone);
    }

    public LocalDate localDate(LocalDateTime utc) {
        return toLocal(utc).toLocalDate();
    }

    public LocalTime localTime(LocalDateTime utc) {
        return toLocal(utc).toLocalTime();
    }
}
