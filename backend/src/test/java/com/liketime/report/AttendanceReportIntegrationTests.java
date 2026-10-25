package com.liketime.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AttendanceReportIntegrationTests extends ApiTestBase {

    @Autowired
    AttendanceRepository attendance;

    private void mark(Lesson lesson, Student s, AttendanceStatus status) {
        Attendance a = new Attendance(lesson.getId(), s.getId());
        a.markManually(status, null, "test");
        attendance.saveAndFlush(a);
    }

    @Test
    void reportCountsMarksAndUnmarkedLessonsPerStudent() throws Exception {
        String manager = tokenFor("rep_manager", Role.MANAGER);
        Teacher teacher = teacher("rep_teacher", "Преподаватель");
        StudyGroup group = group("Группа Отчёт", teacher.getId());
        Student diligent = student("Алия Прилежная");
        Student skipper = student("Бакыт Прогульщик");
        enroll(group, diligent, time.today().minusDays(60));
        enroll(group, skipper, time.today().minusDays(60));
        Lesson l1 = lessonAt(group, teacher.getId(), time.nowUtc().minusDays(5).withNano(0));
        Lesson l2 = lessonAt(group, teacher.getId(), time.nowUtc().minusDays(4).withNano(0));
        Lesson l3 = lessonAt(group, teacher.getId(), time.nowUtc().minusDays(3).withNano(0));
        mark(l1, diligent, AttendanceStatus.PRESENT);
        mark(l2, diligent, AttendanceStatus.LATE);
        mark(l3, diligent, AttendanceStatus.PRESENT);
        mark(l1, skipper, AttendanceStatus.ABSENT);
        mark(l2, skipper, AttendanceStatus.EXCUSED);
        LocalDate from = time.today().minusDays(10);
        LocalDate to = time.today();

        getAs(manager, "/api/reports/attendance?from=" + from + "&to=" + to + "&groupId=" + group.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].studentName").value("Алия Прилежная"))
                .andExpect(jsonPath("$[0].lessons").value(3))
                .andExpect(jsonPath("$[0].present").value(2))
                .andExpect(jsonPath("$[0].late").value(1))
                .andExpect(jsonPath("$[0].rate").value(1.0))
                .andExpect(jsonPath("$[1].studentName").value("Бакыт Прогульщик"))
                .andExpect(jsonPath("$[1].absent").value(1))
                .andExpect(jsonPath("$[1].excused").value(1))
                .andExpect(jsonPath("$[1].unmarked").value(1))
                .andExpect(jsonPath("$[1].rate").value(0.0));
    }

    @Test
    void cancelledAndFutureLessonsAreNotCounted() throws Exception {
        String manager = tokenFor("rep_manager2", Role.MANAGER);
        Teacher teacher = teacher("rep_teacher2", "Преподаватель");
        StudyGroup group = group("Группа Отчёт 2", teacher.getId());
        Student student = student("Ученик");
        enroll(group, student, time.today().minusDays(60));
        Lesson cancelled = lessonAt(group, teacher.getId(), time.nowUtc().minusDays(2).withNano(0));
        cancelled.setStatus(LessonStatus.CANCELLED);
        lessons.saveAndFlush(cancelled);
        lessonAt(group, teacher.getId(), time.nowUtc().plusDays(2).withNano(0));
        lessonAt(group, teacher.getId(), time.nowUtc().minusDays(1).withNano(0));

        getAs(manager, "/api/reports/attendance?from=" + time.today().minusDays(5) + "&to=" + time.today().plusDays(5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lessons").value(1));
    }

    @Test
    void studentWhoJoinedLaterIsNotBlamedForEarlierLessons() throws Exception {
        String manager = tokenFor("rep_manager3", Role.MANAGER);
        Teacher teacher = teacher("rep_teacher3", "Преподаватель");
        StudyGroup group = group("Группа Отчёт 3", teacher.getId());
        Student newcomer = student("Новичок");
        enroll(group, newcomer, time.today().minusDays(2));
        lessonAt(group, teacher.getId(), time.nowUtc().minusDays(6).withNano(0));
        lessonAt(group, teacher.getId(), time.nowUtc().minusDays(1).withNano(0));

        getAs(manager, "/api/reports/attendance?from=" + time.today().minusDays(10) + "&to=" + time.today())
                .andExpect(jsonPath("$[0].lessons").value(1));
    }

    @Test
    void csvAndPermissions() throws Exception {
        String manager = tokenFor("rep_manager4", Role.MANAGER);
        Teacher teacher = teacher("rep_teacher4", "Преподаватель");
        String teacherToken = login("rep_teacher4");
        StudyGroup group = group("Группа Отчёт 4", teacher.getId());
        Student student = student("Ученик CSV");
        enroll(group, student, time.today().minusDays(60));
        Lesson lesson = lessonAt(group, teacher.getId(), time.nowUtc().minusDays(1).withNano(0));
        mark(lesson, student, AttendanceStatus.PRESENT);
        String range = "from=" + time.today().minusDays(5) + "&to=" + time.today();

        String csv = mvc.perform(get("/api/reports/attendance.csv?" + range)
                        .header("Authorization", bearer(manager)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿").contains("Группа Отчёт 4;Ученик CSV;1;1;0;0;0;0;100");

        getAs(teacherToken, "/api/reports/attendance?" + range).andExpect(status().isForbidden());
        getAs(manager, "/api/reports/attendance?from=" + time.today() + "&to=" + time.today().minusDays(1))
                .andExpect(status().isBadRequest());
    }
}
