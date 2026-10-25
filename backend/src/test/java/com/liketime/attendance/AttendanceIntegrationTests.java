package com.liketime.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.liketime.ApiTestBase;
import com.liketime.auth.Role;
import com.liketime.group.StudyGroup;
import com.liketime.schedule.Lesson;
import com.liketime.schedule.LessonStatus;
import com.liketime.student.Student;
import com.liketime.teacher.Teacher;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AttendanceIntegrationTests extends ApiTestBase {

    @Autowired
    AttendanceRepository attendance;

    private String url(Lesson lesson) {
        return "/api/lessons/" + lesson.getId() + "/attendance";
    }

    @Test
    void rosterListsMembersOnLessonDateOnly() throws Exception {
        Teacher teacher = teacher("a_teacher1", "Преподаватель");
        StudyGroup group = group("Группа A1", teacher.getId());
        Lesson lesson = lessonStartedAgo(group, teacher.getId(), 20);
        Student present = student("Ученик Присутствующий");
        Student later = student("Ученик Позже");
        enroll(group, present, time.today().minusDays(10));
        enroll(group, later, time.today().plusDays(5));
        String token = login("a_teacher1");

        JsonNode roster = read(getAs(token, url(lesson)).andExpect(status().isOk()));

        assertThat(roster.get("students")).hasSize(1);
        assertThat(roster.get("students").get(0).get("fullName").asText()).isEqualTo("Ученик Присутствующий");
        assertThat(roster.get("students").get(0).get("status").isNull()).isTrue();
    }

    @Test
    void teacherMarksStudentAndLessonBecomesHeld() throws Exception {
        Teacher teacher = teacher("a_teacher2", "Преподаватель");
        StudyGroup group = group("Группа A2", teacher.getId());
        Lesson lesson = lessonStartedAgo(group, teacher.getId(), 20);
        Student student = student("Ученик 2");
        enroll(group, student, time.today().minusDays(10));
        String token = login("a_teacher2");

        putAs(token, url(lesson) + "/" + student.getId(), Map.of("status", "PRESENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRESENT"))
                .andExpect(jsonPath("$.source").value("MANUAL"));

        assertThat(lessons.findById(lesson.getId()).orElseThrow().getStatus()).isEqualTo(LessonStatus.HELD);
    }

    @Test
    void absentMarkDoesNotMakeLessonHeld() throws Exception {
        Teacher teacher = teacher("a_teacher3", "Преподаватель");
        StudyGroup group = group("Группа A3", teacher.getId());
        Lesson lesson = lessonStartedAgo(group, teacher.getId(), 20);
        Student student = student("Ученик 3");
        enroll(group, student, time.today().minusDays(10));
        String token = login("a_teacher3");

        putAs(token, url(lesson) + "/" + student.getId(), Map.of("status", "ABSENT", "note", "болеет"))
                .andExpect(status().isOk());

        assertThat(lessons.findById(lesson.getId()).orElseThrow().getStatus()).isEqualTo(LessonStatus.PLANNED);
    }

    @Test
    void teacherCannotSeeOrMarkForeignLesson() throws Exception {
        teacher("a_teacher4", "Первый");
        Teacher other = teacher("a_teacher4b", "Второй");
        StudyGroup group = group("Группа A4", other.getId());
        Lesson lesson = lessonStartedAgo(group, other.getId(), 20);
        Student student = student("Ученик 4");
        enroll(group, student, time.today().minusDays(10));
        String token = login("a_teacher4");

        getAs(token, url(lesson)).andExpect(status().isNotFound());
        putAs(token, url(lesson) + "/" + student.getId(), Map.of("status", "PRESENT"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotMarkCancelledOrFutureLesson() throws Exception {
        String token = tokenFor("a_manager5", Role.MANAGER);
        Teacher teacher = teacher("a_teacher5", "Преподаватель");
        StudyGroup group = group("Группа A5", teacher.getId());
        Student student = student("Ученик 5");
        enroll(group, student, time.today().minusDays(10));
        Lesson cancelled = lessonStartedAgo(group, teacher.getId(), 20);
        cancelled.setStatus(LessonStatus.CANCELLED);
        lessons.saveAndFlush(cancelled);
        Lesson future = lessonAt(group, teacher.getId(), time.nowUtc().plusDays(1).withNano(0));

        putAs(token, url(cancelled) + "/" + student.getId(), Map.of("status", "PRESENT"))
                .andExpect(status().isConflict());
        putAs(token, url(future) + "/" + student.getId(), Map.of("status", "PRESENT"))
                .andExpect(status().isConflict());
    }

    @Test
    void studentOutsideGroupCannotBeMarked() throws Exception {
        String token = tokenFor("a_manager6", Role.MANAGER);
        Teacher teacher = teacher("a_teacher6", "Преподаватель");
        StudyGroup group = group("Группа A6", teacher.getId());
        Lesson lesson = lessonStartedAgo(group, teacher.getId(), 20);
        Student stranger = student("Посторонний");

        putAs(token, url(lesson) + "/" + stranger.getId(), Map.of("status", "PRESENT"))
                .andExpect(status().isConflict());
    }

    @Test
    void teacherCannotEditOldAttendanceButStaffCan() throws Exception {
        Teacher teacher = teacher("a_teacher7", "Преподаватель");
        String staffToken = tokenFor("a_manager7", Role.MANAGER);
        StudyGroup group = group("Группа A7", teacher.getId());
        Student student = student("Ученик 7");
        enroll(group, student, time.today().minusDays(30));
        Lesson old = lessonAt(group, teacher.getId(), time.nowUtc().minusDays(10).withNano(0));
        String teacherToken = login("a_teacher7");

        putAs(teacherToken, url(old) + "/" + student.getId(), Map.of("status", "PRESENT"))
                .andExpect(status().isForbidden());
        putAs(staffToken, url(old) + "/" + student.getId(), Map.of("status", "PRESENT"))
                .andExpect(status().isOk());
    }

    @Test
    void bulkMarkIsAllOrNothing() throws Exception {
        Teacher teacher = teacher("a_teacher8", "Преподаватель");
        StudyGroup group = group("Группа A8", teacher.getId());
        Lesson lesson = lessonStartedAgo(group, teacher.getId(), 20);
        Student first = student("Ученик 8a");
        Student stranger = student("Ученик 8b");
        enroll(group, first, time.today().minusDays(10));
        String token = login("a_teacher8");

        putAs(token, url(lesson), Map.of("records", List.of(
                Map.of("studentId", first.getId(), "status", "PRESENT"),
                Map.of("studentId", stranger.getId(), "status", "PRESENT"))))
                .andExpect(status().isConflict());

        // Все ученики проверяются до записи, поэтому отметка первого (допустимого) ученика не сохранилась
        assertThat(attendance.findByLessonId(lesson.getId())).isEmpty();
    }

    @Test
    void clearingMarkRemovesItAndIsAudited() throws Exception {
        String token = tokenFor("a_manager9", Role.MANAGER);
        String adminToken = tokenFor("a_admin9", Role.ADMIN);
        Teacher teacher = teacher("a_teacher9", "Преподаватель");
        StudyGroup group = group("Группа A9", teacher.getId());
        Lesson lesson = lessonStartedAgo(group, teacher.getId(), 20);
        Student student = student("Ученик 9");
        enroll(group, student, time.today().minusDays(10));

        putAs(token, url(lesson) + "/" + student.getId(), Map.of("status", "PRESENT")).andExpect(status().isOk());
        mvc.perform(delete(url(lesson) + "/" + student.getId()).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        assertThat(attendance.findByLessonIdAndStudentId(lesson.getId(), student.getId())).isEmpty();
        getAs(adminToken, "/api/audit?entity=lesson&entityId=" + lesson.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action=='ATTENDANCE_MARK')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action=='ATTENDANCE_CLEAR')]").isNotEmpty());
    }

    @Test
    void cancellingLessonWithVisitsIsRejected() throws Exception {
        String token = tokenFor("a_manager10", Role.MANAGER);
        Teacher teacher = teacher("a_teacher10", "Преподаватель");
        StudyGroup group = group("Группа A10", teacher.getId());
        Lesson lesson = lessonStartedAgo(group, teacher.getId(), 20);
        Student student = student("Ученик 10");
        enroll(group, student, LocalDate.now().minusDays(10));
        putAs(token, url(lesson) + "/" + student.getId(), Map.of("status", "PRESENT")).andExpect(status().isOk());

        putAs(token, "/api/lessons/" + lesson.getId() + "/status", Map.of("status", "CANCELLED"))
                .andExpect(status().isConflict());
    }

    @Test
    void auditLogIsAdminOnly() throws Exception {
        String token = tokenFor("a_manager11", Role.MANAGER);

        getAs(token, "/api/audit").andExpect(status().isForbidden());
    }
}
