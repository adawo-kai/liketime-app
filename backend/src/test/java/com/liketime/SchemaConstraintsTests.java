package com.liketime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ограничения самой базы (миграции V3-V6). Сервисы проверяют те же правила раньше и дают понятные
 * сообщения, но база должна держать их и при ручном SQL или ошибке в коде.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaConstraintsTests {

    @Autowired
    JdbcTemplate jdbc;

    long teacherId;
    long groupId;
    long studentId;
    long lessonId;
    long deviceId;
    long periodId;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO teachers (full_name, status, created_at, updated_at) VALUES ('Т', 'ACTIVE', NOW(6), NOW(6))");
        teacherId = id("SELECT MAX(id) FROM teachers");
        jdbc.update("INSERT INTO students (full_name, status, created_at, updated_at) VALUES ('У', 'ACTIVE', NOW(6), NOW(6))");
        studentId = id("SELECT MAX(id) FROM students");
        jdbc.update("INSERT INTO study_groups (name, teacher_id, status, created_at, updated_at) VALUES ('Группа схемы', ?, 'ACTIVE', NOW(6), NOW(6))", teacherId);
        groupId = id("SELECT MAX(id) FROM study_groups");
        jdbc.update("INSERT INTO lessons (group_id, teacher_id, starts_at, ends_at, status, origin, created_at, updated_at) "
                + "VALUES (?, ?, '2026-01-10 03:00:00', '2026-01-10 04:00:00', 'PLANNED', 'MANUAL', NOW(6), NOW(6))", groupId, teacherId);
        lessonId = id("SELECT MAX(id) FROM lessons");
        jdbc.update("INSERT INTO devices (name, serial_number, status, created_at, updated_at) VALUES ('Д', 'SN-SCHEMA-1', 'ACTIVE', NOW(6), NOW(6))");
        deviceId = id("SELECT MAX(id) FROM devices");
        jdbc.update("INSERT INTO salary_periods (starts_on, ends_on, status, created_at, updated_at) VALUES ('2026-01-01', '2026-01-31', 'OPEN', NOW(6), NOW(6))");
        periodId = id("SELECT MAX(id) FROM salary_periods");
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    @Test
    void allTablesFromMigrationsThreeToSixExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", String.class);

        assertThat(tables).contains("schedule_slots", "lessons", "audit_log", "attendance", "device_persons",
                "device_events", "salary_rules", "salary_periods", "salary_lines", "salary_adjustments");
    }

    @Test
    void lessonMustEndAfterItStarts() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO lessons (group_id, starts_at, ends_at, status, origin, created_at, updated_at) "
                + "VALUES (?, '2026-01-11 03:00:00', '2026-01-11 03:00:00', 'PLANNED', 'MANUAL', NOW(6), NOW(6))", groupId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_lessons_time");
    }

    @Test
    void groupCannotHaveTwoLessonsStartingAtTheSameMoment() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO lessons (group_id, starts_at, ends_at, status, origin, created_at, updated_at) "
                + "VALUES (?, '2026-01-10 03:00:00', '2026-01-10 04:00:00', 'PLANNED', 'MANUAL', NOW(6), NOW(6))", groupId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void lessonStatusIsRestricted() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO lessons (group_id, starts_at, ends_at, status, origin, created_at, updated_at) "
                + "VALUES (?, '2026-01-12 03:00:00', '2026-01-12 04:00:00', 'DONE', 'MANUAL', NOW(6), NOW(6))", groupId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_lessons_status");
    }

    @Test
    void slotWeekdayAndTimesAreValidated() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO schedule_slots (group_id, weekday, start_time, end_time, active, created_at, updated_at) "
                + "VALUES (?, 8, '10:00', '11:00', TRUE, NOW(6), NOW(6))", groupId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_schedule_slots_weekday");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO schedule_slots (group_id, weekday, start_time, end_time, active, created_at, updated_at) "
                + "VALUES (?, 1, '11:00', '10:00', TRUE, NOW(6), NOW(6))", groupId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_schedule_slots_time");
    }

    @Test
    void studentHasOnlyOneAttendanceRecordPerLesson() {
        jdbc.update("INSERT INTO attendance (lesson_id, student_id, status, source, created_at, updated_at) "
                + "VALUES (?, ?, 'PRESENT', 'MANUAL', NOW(6), NOW(6))", lessonId, studentId);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO attendance (lesson_id, student_id, status, source, created_at, updated_at) "
                + "VALUES (?, ?, 'ABSENT', 'MANUAL', NOW(6), NOW(6))", lessonId, studentId))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void attendanceStatusAndSourceAreRestricted() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO attendance (lesson_id, student_id, status, source, created_at, updated_at) "
                + "VALUES (?, ?, 'MAYBE', 'MANUAL', NOW(6), NOW(6))", lessonId, studentId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_attendance_status");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO attendance (lesson_id, student_id, status, source, created_at, updated_at) "
                + "VALUES (?, ?, 'PRESENT', 'GUESS', NOW(6), NOW(6))", lessonId, studentId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_attendance_source");
    }

    @Test
    void deviceEventIdIsUniquePerDeviceAndPayloadMustBeJson() {
        String insert = "INSERT INTO device_events (device_id, external_event_id, event_time, received_at, match_status, raw_payload) "
                + "VALUES (?, ?, NOW(6), NOW(6), 'MATCHED', ?)";
        jdbc.update(insert, deviceId, "evt-1", "{\"a\":1}");

        assertThatThrownBy(() -> jdbc.update(insert, deviceId, "evt-1", "{}")).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update(insert, deviceId, "evt-2", "это не json"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_device_events_payload");
    }

    @Test
    void oneStudentCannotBeLinkedToTwoFacesOnTheSameDevice() {
        jdbc.update("INSERT INTO device_persons (device_id, external_person_id, student_id, created_at, updated_at) "
                + "VALUES (?, 'P-1', ?, NOW(6), NOW(6))", deviceId, studentId);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO device_persons (device_id, external_person_id, student_id, created_at, updated_at) "
                + "VALUES (?, 'P-2', ?, NOW(6), NOW(6))", deviceId, studentId)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void salaryRuleAmountAndDatesAreValidated() {
        String insert = "INSERT INTO salary_rules (rule_type, amount, effective_from, effective_to, active, created_at, updated_at) "
                + "VALUES ('PER_VISIT', ?, ?, ?, TRUE, NOW(6), NOW(6))";

        assertThatThrownBy(() -> jdbc.update(insert, -1, "2026-01-01", null))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_salary_rules_amount");
        assertThatThrownBy(() -> jdbc.update(insert, 100, "2026-02-01", "2026-01-01"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_salary_rules_dates");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO salary_rules (rule_type, amount, effective_from, active, created_at, updated_at) "
                + "VALUES ('PER_HOUR', 100, '2026-01-01', TRUE, NOW(6), NOW(6))"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_salary_rules_type");
    }

    @Test
    void salaryPeriodDatesStatusAndUniquenessAreValidated() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO salary_periods (starts_on, ends_on, status, created_at, updated_at) "
                + "VALUES ('2026-03-31', '2026-03-01', 'OPEN', NOW(6), NOW(6))"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_salary_periods_dates");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO salary_periods (starts_on, ends_on, status, created_at, updated_at) "
                + "VALUES ('2026-04-01', '2026-04-30', 'DONE', NOW(6), NOW(6))"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_salary_periods_status");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO salary_periods (starts_on, ends_on, status, created_at, updated_at) "
                + "VALUES ('2026-01-01', '2026-01-15', 'OPEN', NOW(6), NOW(6))"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void salaryLineIsUniquePerLessonInPeriodAndNeedsVisits() {
        String insert = "INSERT INTO salary_lines (period_id, teacher_id, lesson_id, group_id, lesson_date, visits, unit_amount, amount, calculated_at) "
                + "VALUES (?, ?, ?, ?, '2026-01-10', ?, 100, 100, NOW(6))";
        jdbc.update(insert, periodId, teacherId, lessonId, groupId, 1);

        assertThatThrownBy(() -> jdbc.update(insert, periodId, teacherId, lessonId, groupId, 1))
                .isInstanceOf(DataAccessException.class);

        // строка начисления с нулём приходов бессмысленна: такие занятия в расчёт не попадают
        jdbc.update("DELETE FROM salary_lines WHERE period_id = ?", periodId);
        assertThatThrownBy(() -> jdbc.update(insert, periodId, teacherId, lessonId, groupId, 0))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_salary_lines_visits");
    }

    @Test
    void zeroAdjustmentIsRejected() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO salary_adjustments (period_id, teacher_id, amount, reason, created_by, created_at) "
                + "VALUES (?, ?, 0, 'ноль', 'x', NOW(6))", periodId, teacherId))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_salary_adjustments_amount");
    }

    @Test
    void auditDetailsMustBeJson() {
        jdbc.update("INSERT INTO audit_log (action, entity, details, created_at) VALUES ('T', 'test', '{\"ok\":true}', NOW(6))");

        assertThatThrownBy(() -> jdbc.update("INSERT INTO audit_log (action, entity, details, created_at) VALUES ('T', 'test', 'не json', NOW(6))"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("ck_audit_details");
    }

    @Test
    void referencedRowsCannotBeDeleted() {
        // ON DELETE RESTRICT: у занятия есть зависимости, удалить группу и преподавателя нельзя
        assertThatThrownBy(() -> jdbc.update("DELETE FROM study_groups WHERE id = ?", groupId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM teachers WHERE id = ?", teacherId)).isInstanceOf(DataAccessException.class);
    }
}
