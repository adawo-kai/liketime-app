package com.liketime.attendance;

import java.util.List;

public enum AttendanceStatus {
    PRESENT,
    LATE,
    ABSENT,
    EXCUSED;

    /** Приход ученика на занятие: именно за такие отметки начисляется зарплата */
    public static final List<AttendanceStatus> VISITS = List.of(PRESENT, LATE);

    public boolean countsAsVisit() {
        return VISITS.contains(this);
    }
}
