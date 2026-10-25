package com.liketime.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.liketime.ApiTestBase;
import com.liketime.attendance.Attendance;
import com.liketime.attendance.AttendanceRepository;
import com.liketime.attendance.AttendanceStatus;
import com.liketime.auth.Role;
import com.liketime.group.StudyGroup;
import com.liketime.schedule.Lesson;
import com.liketime.schedule.LessonStatus;
import com.liketime.student.Student;
import com.liketime.teacher.Teacher;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SalaryIntegrationTests extends ApiTestBase {

    private static final LocalDate LONG_AGO = LocalDate.of(2020, 1, 1);

    @Autowired
    AttendanceRepository attendance;

    String admin;
    Teacher teacher;
    StudyGroup group;
    Student s1;
    Student s2;
    Student s3;

    @BeforeEach
    void setUp() throws Exception {
        admin = tokenFor("sal_admin", Role.ADMIN);
        teacher = teacher("sal_teacher", "Айгуль Преподаватель");
        group = group("Группа Зарплата", teacher.getId());
        s1 = student("Ученик 1");
        s2 = student("Ученик 2");
        s3 = student("Ученик 3");
        for (Student s : List.of(s1, s2, s3)) {
            enroll(group, s, time.today().minusDays(100));
        }
    }

    // ----- вспомогательные методы -----

    /** Проведённое занятие N дней назад: present приходят, absent пропускают */
    private Lesson held(StudyGroup g, Teacher t, int daysAgo, List<Student> present, List<Student> absent) {
        Lesson lesson = lessonAt(g, t == null ? null : t.getId(), time.nowUtc().minusDays(daysAgo).withNano(0));
        lesson.setStatus(LessonStatus.HELD);
        lessons.saveAndFlush(lesson);
        for (Student s : present) {
            mark(lesson, s, AttendanceStatus.PRESENT);
        }
        for (Student s : absent) {
            mark(lesson, s, AttendanceStatus.ABSENT);
        }
        return lesson;
    }

    private void mark(Lesson lesson, Student s, AttendanceStatus status) {
        Attendance a = new Attendance(lesson.getId(), s.getId());
        a.markManually(status, null, "test");
        attendance.saveAndFlush(a);
    }

    private long rule(Long teacherId, Long groupId, String amount, LocalDate from, LocalDate to) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("teacherId", teacherId);
        body.put("groupId", groupId);
        body.put("amount", new BigDecimal(amount));
        body.put("effectiveFrom", from.toString());
        body.put("effectiveTo", to == null ? null : to.toString());
        return read(postAs(admin, "/api/salary/rules", body).andExpect(status().isCreated())).get("id").asLong();
    }

    /** Период, закончившийся 5 дней назад и начавшийся 30 дней назад */
    private long closedPeriod() throws Exception {
        return read(postAs(admin, "/api/salary/periods", Map.of(
                "startsOn", time.today().minusDays(30).toString(),
                "endsOn", time.today().minusDays(5).toString())).andExpect(status().isCreated())).get("id").asLong();
    }

    private void calculate(long periodId) throws Exception {
        postAs(admin, "/api/salary/periods/" + periodId + "/calculate", Map.of()).andExpect(status().isOk());
    }

    // ----- расчёт -----

    @Test
    void hundredSomPerVisitCountsPresentAndLateButNotAbsent() throws Exception {
        rule(null, null, "100.00", LONG_AGO, null);
        Lesson lesson = held(group, teacher, 10, List.of(s1, s2), List.of(s3));
        mark(lesson, student("Ученик Опоздавший"), AttendanceStatus.LATE);
        long period = closedPeriod();

        postAs(admin, "/api/salary/periods/" + period + "/calculate", Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines").value(1))
                .andExpect(jsonPath("$.accrued").value(300.0));

        getAs(admin, "/api/salary/periods/" + period)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teachers[0].visits").value(3))
                .andExpect(jsonPath("$.teachers[0].total").value(300.0))
                .andExpect(jsonPath("$.linesWithoutRule").value(0));
    }

    @Test
    void onlyHeldLessonsWithTeacherAndVisitsArePaid() throws Exception {
        rule(null, null, "100.00", LONG_AGO, null);
        held(group, teacher, 10, List.of(s1), List.of());
        // запланированное занятие с отметкой, отменённое и без преподавателя не оплачиваются
        Lesson planned = lessonAt(group, teacher.getId(), time.nowUtc().minusDays(11).withNano(0));
        mark(planned, s1, AttendanceStatus.PRESENT);
        Lesson cancelled = lessonAt(group, teacher.getId(), time.nowUtc().minusDays(12).withNano(0));
        mark(cancelled, s1, AttendanceStatus.PRESENT);
        cancelled.setStatus(LessonStatus.CANCELLED);
        lessons.saveAndFlush(cancelled);
        held(group, null, 13, List.of(s1), List.of());
        held(group, teacher, 14, List.of(), List.of(s1));
        long period = closedPeriod();

        postAs(admin, "/api/salary/periods/" + period + "/calculate", Map.of())
                .andExpect(jsonPath("$.lines").value(1))
                .andExpect(jsonPath("$.accrued").value(100.0));
    }

    @Test
    void groupRuleBeatsTeacherRuleBeatsGlobalRule() throws Exception {
        Teacher other = teacher("sal_teacher2", "Другой преподаватель");
        StudyGroup otherGroup = group("Группа Другая", other.getId());
        enroll(otherGroup, s1, time.today().minusDays(100));
        rule(null, null, "100.00", LONG_AGO, null);
        rule(teacher.getId(), null, "120.00", LONG_AGO, null);
        rule(null, group.getId(), "150.00", LONG_AGO, null);
        held(group, teacher, 10, List.of(s1, s2), List.of());          // группа: 150 * 2
        held(otherGroup, other, 11, List.of(s1), List.of());            // глобальное: 100 * 1
        Lesson viaTeacher = lessonAt(otherGroup, teacher.getId(), time.nowUtc().minusDays(12).withNano(0));
        viaTeacher.setStatus(LessonStatus.HELD);
        lessons.saveAndFlush(viaTeacher);
        mark(viaTeacher, s1, AttendanceStatus.PRESENT);                 // преподаватель: 120 * 1
        long period = closedPeriod();

        postAs(admin, "/api/salary/periods/" + period + "/calculate", Map.of())
                .andExpect(jsonPath("$.accrued").value(300.0 + 100.0 + 120.0));
    }

    @Test
    void ruleVersionsByDateApplyToTheirOwnLessons() throws Exception {
        LocalDate switchDate = time.today().minusDays(15);
        rule(null, null, "80.00", LONG_AGO, switchDate.minusDays(1));
        rule(null, null, "100.00", switchDate, null);
        held(group, teacher, 20, List.of(s1), List.of());   // до перехода: 80
        held(group, teacher, 10, List.of(s1), List.of());   // после перехода: 100
        long period = closedPeriod();

        postAs(admin, "/api/salary/periods/" + period + "/calculate", Map.of())
                .andExpect(jsonPath("$.accrued").value(180.0));
    }

    @Test
    void overlappingRulesOfSameScopeAreRejected() throws Exception {
        rule(null, null, "100.00", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));

        postAs(admin, "/api/salary/rules", Map.of("amount", 90, "effectiveFrom", "2026-06-30"))
                .andExpect(status().isConflict());
        postAs(admin, "/api/salary/rules", Map.of("amount", 90, "effectiveFrom", "2026-07-01"))
                .andExpect(status().isCreated());
    }

    @Test
    void ruleValidation() throws Exception {
        postAs(admin, "/api/salary/rules", Map.of("amount", -5, "effectiveFrom", "2026-01-01"))
                .andExpect(status().isBadRequest());
        postAs(admin, "/api/salary/rules",
                Map.of("amount", 100, "effectiveFrom", "2026-05-01", "effectiveTo", "2026-04-01"))
                .andExpect(status().isBadRequest());
        postAs(admin, "/api/salary/rules", Map.of("amount", 100, "effectiveFrom", "2026-01-01", "groupId", 999999999))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rulesAreReadableByManagerButChangeableOnlyByAdmin() throws Exception {
        String manager = tokenFor("sal_manager", Role.MANAGER);
        rule(null, null, "100.00", LONG_AGO, null);

        getAs(manager, "/api/salary/rules").andExpect(status().isOk()).andExpect(jsonPath("$[0].amount").value(100.0));
        postAs(manager, "/api/salary/rules", Map.of("amount", 1, "effectiveFrom", "2030-01-01"))
                .andExpect(status().isForbidden());
    }

    @Test
    void changingRuleDoesNotChangeAlreadyCalculatedAmountsUntilRecalculated() throws Exception {
        long ruleId = rule(null, null, "100.00", LONG_AGO, null);
        held(group, teacher, 10, List.of(s1, s2), List.of());
        long period = closedPeriod();
        calculate(period);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/salary/rules/" + ruleId).header("Authorization", bearer(admin))
                        .contentType("application/json")
                        .content(json(Map.of("amount", 200, "effectiveFrom", LONG_AGO.toString()))))
                .andExpect(status().isOk());

        getAs(admin, "/api/salary/periods/" + period).andExpect(jsonPath("$.teachers[0].total").value(200.0));
        calculate(period);
        getAs(admin, "/api/salary/periods/" + period).andExpect(jsonPath("$.teachers[0].total").value(400.0));
    }

    // ----- жизненный цикл периода -----

    @Test
    void periodWithoutRuleCannotBeApproved() throws Exception {
        held(group, teacher, 10, List.of(s1), List.of());
        long period = closedPeriod();
        calculate(period);

        getAs(admin, "/api/salary/periods/" + period).andExpect(jsonPath("$.linesWithoutRule").value(1));
        postAs(admin, "/api/salary/periods/" + period + "/approve", Map.of()).andExpect(status().isConflict());

        rule(null, null, "100.00", LONG_AGO, null);
        postAs(admin, "/api/salary/periods/" + period + "/approve", Map.of())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.total").value(100.0));
    }

    @Test
    void periodThatHasNotEndedCannotBeApproved() throws Exception {
        rule(null, null, "100.00", LONG_AGO, null);
        long period = read(postAs(admin, "/api/salary/periods", Map.of(
                "startsOn", time.today().minusDays(3).toString(),
                "endsOn", time.today().plusDays(10).toString())).andExpect(status().isCreated())).get("id").asLong();

        postAs(admin, "/api/salary/periods/" + period + "/approve", Map.of()).andExpect(status().isConflict());
    }

    @Test
    void approvedPeriodLocksAttendanceLessonsAndAdjustmentsUntilReopened() throws Exception {
        rule(null, null, "100.00", LONG_AGO, null);
        Lesson lesson = held(group, teacher, 10, List.of(s1), List.of());
        long period = closedPeriod();
        postAs(admin, "/api/salary/periods/" + period + "/approve", Map.of()).andExpect(status().isOk());

        putAs(admin, "/api/lessons/" + lesson.getId() + "/attendance/" + s2.getId(), Map.of("status", "PRESENT"))
                .andExpect(status().isConflict());
        putAs(admin, "/api/lessons/" + lesson.getId() + "/status", Map.of("status", "CANCELLED"))
                .andExpect(status().isConflict());
        postAs(admin, "/api/lessons", Map.of("groupId", group.getId(), "date", time.today().minusDays(10).toString(),
                "startTime", "10:00", "endTime", "11:00")).andExpect(status().isConflict());
        postAs(admin, "/api/salary/periods/" + period + "/adjustments",
                Map.of("teacherId", teacher.getId(), "amount", 50, "reason", "Премия")).andExpect(status().isConflict());
        postAs(admin, "/api/salary/periods/" + period + "/calculate", Map.of()).andExpect(status().isConflict());

        postAs(admin, "/api/salary/periods/" + period + "/reopen", Map.of())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CALCULATED"));
        putAs(admin, "/api/lessons/" + lesson.getId() + "/attendance/" + s2.getId(), Map.of("status", "PRESENT"))
                .andExpect(status().isOk());
    }

    @Test
    void paidPeriodCannotBeReopened() throws Exception {
        rule(null, null, "100.00", LONG_AGO, null);
        held(group, teacher, 10, List.of(s1), List.of());
        long period = closedPeriod();

        postAs(admin, "/api/salary/periods/" + period + "/pay", Map.of()).andExpect(status().isConflict());
        postAs(admin, "/api/salary/periods/" + period + "/approve", Map.of()).andExpect(status().isOk());
        postAs(admin, "/api/salary/periods/" + period + "/pay", Map.of())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
        postAs(admin, "/api/salary/periods/" + period + "/reopen", Map.of()).andExpect(status().isConflict());
    }

    @Test
    void periodsCannotOverlapAndHaveLimitedLength() throws Exception {
        closedPeriod();

        postAs(admin, "/api/salary/periods", Map.of("startsOn", time.today().minusDays(10).toString(),
                "endsOn", time.today().minusDays(1).toString())).andExpect(status().isConflict());
        postAs(admin, "/api/salary/periods", Map.of("startsOn", time.today().minusDays(300).toString(),
                "endsOn", time.today().minusDays(100).toString())).andExpect(status().isBadRequest());
        postAs(admin, "/api/salary/periods", Map.of("startsOn", time.today().toString(),
                "endsOn", time.today().minusDays(1).toString())).andExpect(status().isBadRequest());
    }

    @Test
    void periodWithAdjustmentsCannotBeDeleted() throws Exception {
        long period = closedPeriod();
        postAs(admin, "/api/salary/periods/" + period + "/adjustments",
                Map.of("teacherId", teacher.getId(), "amount", 50, "reason", "Премия")).andExpect(status().isCreated());

        mvc.perform(delete("/api/salary/periods/" + period).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());
    }

    // ----- корректировки -----

    @Test
    void adjustmentsChangeTheTotal() throws Exception {
        rule(null, null, "100.00", LONG_AGO, null);
        held(group, teacher, 10, List.of(s1, s2), List.of());
        long period = closedPeriod();
        calculate(period);

        postAs(admin, "/api/salary/periods/" + period + "/adjustments",
                Map.of("teacherId", teacher.getId(), "amount", 50, "reason", "Премия за открытый урок"))
                .andExpect(status().isCreated());
        postAs(admin, "/api/salary/periods/" + period + "/adjustments",
                Map.of("teacherId", teacher.getId(), "amount", -20.5, "reason", "Штраф за опоздание"))
                .andExpect(status().isCreated());

        getAs(admin, "/api/salary/periods/" + period)
                .andExpect(jsonPath("$.teachers[0].accrued").value(200.0))
                .andExpect(jsonPath("$.teachers[0].adjustments").value(29.5))
                .andExpect(jsonPath("$.teachers[0].total").value(229.5));
    }

    @Test
    void adjustmentValidationAndPermissions() throws Exception {
        String manager = tokenFor("sal_manager2", Role.MANAGER);
        long period = closedPeriod();

        postAs(admin, "/api/salary/periods/" + period + "/adjustments",
                Map.of("teacherId", teacher.getId(), "amount", 0, "reason", "Ноль")).andExpect(status().isBadRequest());
        postAs(admin, "/api/salary/periods/" + period + "/adjustments",
                Map.of("teacherId", teacher.getId(), "amount", 10, "reason", "  ")).andExpect(status().isBadRequest());
        postAs(manager, "/api/salary/periods/" + period + "/adjustments",
                Map.of("teacherId", teacher.getId(), "amount", 10, "reason", "Премия")).andExpect(status().isForbidden());
    }

    // ----- кто что видит -----

    @Test
    void teacherSeesOwnSalaryOnlyAfterApproval() throws Exception {
        rule(null, null, "100.00", LONG_AGO, null);
        held(group, teacher, 10, List.of(s1, s2), List.of());
        Teacher other = teacher("sal_teacher3", "Чужой преподаватель");
        String teacherToken = login("sal_teacher");
        String otherToken = login("sal_teacher3");
        long period = closedPeriod();
        calculate(period);
        String detailUrl = "/api/salary/periods/" + period + "/teachers/" + teacher.getId();

        getAs(teacherToken, detailUrl).andExpect(status().isNotFound());
        getAs(teacherToken, "/api/salary/my").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        postAs(admin, "/api/salary/periods/" + period + "/approve", Map.of()).andExpect(status().isOk());

        getAs(teacherToken, detailUrl).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(200.0))
                .andExpect(jsonPath("$.lines[0].groupName").value("Группа Зарплата"));
        getAs(teacherToken, "/api/salary/my").andExpect(jsonPath("$[0].total").value(200.0));
        getAs(otherToken, detailUrl).andExpect(status().isNotFound());
        getAs(otherToken, "/api/salary/periods/" + period + "/teachers/" + other.getId()).andExpect(status().isOk());
        getAs(teacherToken, "/api/salary/periods").andExpect(status().isForbidden());
    }

    @Test
    void csvExportIsExcelFriendly() throws Exception {
        rule(null, null, "100.00", LONG_AGO, null);
        held(group, teacher, 10, List.of(s1, s2, s3), List.of());
        long period = closedPeriod();
        calculate(period);

        String csv = mvc.perform(get("/api/salary/periods/" + period + "/export.csv")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿");
        assertThat(csv).contains("Айгуль Преподаватель;1;3;300,00;0,00;300,00");
        assertThat(csv).contains("Итого;;;300,00;0,00;300,00");

        String details = mvc.perform(get("/api/salary/periods/" + period + "/export-details.csv")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(details).contains("Начисление").contains("Группа Зарплата").contains("100,00");
    }

    @Test
    void csvNeutralisesSpreadsheetFormulas() throws Exception {
        Teacher sneaky = teacher("sal_sneaky", "=HYPERLINK(\"http://evil\")");
        rule(null, null, "100.00", LONG_AGO, null);
        StudyGroup g = group("Группа Формула", sneaky.getId());
        enroll(g, s1, time.today().minusDays(100));
        held(g, sneaky, 10, List.of(s1), List.of());
        long period = closedPeriod();
        calculate(period);

        String csv = mvc.perform(get("/api/salary/periods/" + period + "/export.csv")
                        .header("Authorization", bearer(admin)))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(csv).doesNotContain("\n=HYPERLINK").contains("'=HYPERLINK");
    }

    @Test
    void everyChangeIsWrittenToAuditLog() throws Exception {
        long ruleId = rule(null, null, "100.00", LONG_AGO, null);
        long period = closedPeriod();
        calculate(period);

        getAs(admin, "/api/audit?entity=salary_rule&entityId=" + ruleId)
                .andExpect(jsonPath("$[0].action").value("SALARY_RULE_CREATE"))
                .andExpect(jsonPath("$[0].actor").value("sal_admin"));
        getAs(admin, "/api/audit?entity=salary_period&entityId=" + period)
                .andExpect(jsonPath("$[?(@.action=='PERIOD_CALCULATE')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action=='PERIOD_CREATE')]").isNotEmpty());
    }
}
