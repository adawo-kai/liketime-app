package com.liketime.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.liketime.ApiTestBase;
import com.liketime.auth.Role;
import com.liketime.group.StudyGroup;
import com.liketime.teacher.Teacher;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScheduleIntegrationTests extends ApiTestBase {

    private LocalDate nextMonday() {
        return time.today().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private Map<String, Object> slot(Long groupId, int weekday, String start, String end) {
        return Map.of("groupId", groupId, "weekday", weekday, "startTime", start, "endTime", end);
    }

    @Test
    void generationCreatesLessonsOnceAndIsRepeatable() throws Exception {
        String token = tokenFor("s_manager1", Role.MANAGER);
        Teacher teacher = teacher("s_teacher1", "Преподаватель 1");
        StudyGroup group = group("Группа S1", teacher.getId());
        postAs(token, "/api/schedule/slots", slot(group.getId(), 1, "09:00", "10:30"))
                .andExpect(status().isCreated());

        LocalDate from = nextMonday();
        Map<String, Object> request = Map.of("from", from.toString(), "to", from.plusDays(13).toString());

        postAs(token, "/api/schedule/generate", request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.alreadyExisted").value(0));
        postAs(token, "/api/schedule/generate", request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.alreadyExisted").value(2));

        assertThat(lessons.search(time.startOfDayUtc(from), time.startOfDayUtc(from.plusDays(14)), group.getId(), null))
                .hasSize(2)
                .allSatisfy(l -> assertThat(l.getTeacherId()).isEqualTo(teacher.getId()));
    }

    @Test
    void lessonTimeIsStoredInUtcAndShownInBishkekTime() throws Exception {
        String token = tokenFor("s_manager2", Role.MANAGER);
        StudyGroup group = group("Группа S2", null);
        postAs(token, "/api/schedule/slots", slot(group.getId(), 1, "09:00", "10:00")).andExpect(status().isCreated());
        LocalDate monday = nextMonday();
        postAs(token, "/api/schedule/generate", Map.of("from", monday.toString(), "to", monday.toString()))
                .andExpect(status().isOk());

        // Бишкек UTC+6: 09:00 местного времени это 03:00 UTC
        getAs(token, "/api/lessons?from=" + monday + "&to=" + monday)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startTime").value("09:00:00"))
                .andExpect(jsonPath("$[0].date").value(monday.toString()))
                .andExpect(jsonPath("$[0].startsAt").value(monday + "T03:00:00Z"));
    }

    @Test
    void generationReportsTeacherConflictInsteadOfDoubleBooking() throws Exception {
        String token = tokenFor("s_manager3", Role.MANAGER);
        Teacher teacher = teacher("s_teacher3", "Занятый преподаватель");
        StudyGroup first = group("Группа S3a", teacher.getId());
        StudyGroup second = group("Группа S3b", teacher.getId());
        postAs(token, "/api/schedule/slots", slot(first.getId(), 1, "10:00", "11:00")).andExpect(status().isCreated());
        postAs(token, "/api/schedule/slots", slot(second.getId(), 1, "10:30", "11:30")).andExpect(status().isCreated());
        LocalDate monday = nextMonday();

        JsonNode result = read(postAs(token, "/api/schedule/generate",
                Map.of("from", monday.toString(), "to", monday.toString())).andExpect(status().isOk()));

        assertThat(result.get("created").asInt()).isEqualTo(1);
        assertThat(result.get("conflicts")).hasSize(1);
        assertThat(result.get("conflicts").get(0).asText()).contains("преподаватель занят");
    }

    @Test
    void generationRangeIsLimited() throws Exception {
        String token = tokenFor("s_manager4", Role.MANAGER);
        LocalDate from = nextMonday();

        postAs(token, "/api/schedule/generate", Map.of("from", from.toString(), "to", from.plusDays(120).toString()))
                .andExpect(status().isBadRequest());
        postAs(token, "/api/schedule/generate", Map.of("from", from.toString(), "to", from.minusDays(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void slotValidationAndDuplicates() throws Exception {
        String token = tokenFor("s_manager5", Role.MANAGER);
        StudyGroup group = group("Группа S5", null);

        postAs(token, "/api/schedule/slots", slot(group.getId(), 1, "11:00", "10:00")).andExpect(status().isBadRequest());
        postAs(token, "/api/schedule/slots", slot(group.getId(), 8, "10:00", "11:00")).andExpect(status().isBadRequest());
        postAs(token, "/api/schedule/slots", slot(group.getId(), 2, "10:00", "11:00")).andExpect(status().isCreated());
        postAs(token, "/api/schedule/slots", slot(group.getId(), 2, "10:00", "12:00")).andExpect(status().isConflict());
    }

    @Test
    void deactivatedSlotNoLongerGeneratesLessons() throws Exception {
        String token = tokenFor("s_manager6", Role.MANAGER);
        StudyGroup group = group("Группа S6", null);
        long slotId = read(postAs(token, "/api/schedule/slots", slot(group.getId(), 1, "09:00", "10:00"))
                .andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/schedule/slots/" + slotId).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        LocalDate monday = nextMonday();
        postAs(token, "/api/schedule/generate", Map.of("from", monday.toString(), "to", monday.toString()))
                .andExpect(jsonPath("$.created").value(0));
    }

    @Test
    void teacherSeesOnlyOwnLessons() throws Exception {
        Teacher mine = teacher("s_teacher7a", "Мой");
        Teacher other = teacher("s_teacher7b", "Чужой");
        StudyGroup myGroup = group("Группа S7a", mine.getId());
        StudyGroup otherGroup = group("Группа S7b", other.getId());
        lessonStartedAgo(myGroup, mine.getId(), 30);
        lessonStartedAgo(otherGroup, other.getId(), 30);
        String token = login("s_teacher7a");
        LocalDate today = time.today();

        getAs(token, "/api/lessons?from=" + today.minusDays(1) + "&to=" + today.plusDays(1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].groupName").value("Группа S7a"));
    }

    @Test
    void teacherCannotChangeScheduleOrCreateLessons() throws Exception {
        Teacher teacher = teacher("s_teacher8", "Преподаватель");
        StudyGroup group = group("Группа S8", teacher.getId());
        String token = login("s_teacher8");

        postAs(token, "/api/schedule/slots", slot(group.getId(), 1, "09:00", "10:00")).andExpect(status().isForbidden());
        postAs(token, "/api/schedule/generate", Map.of("from", nextMonday().toString(), "to", nextMonday().toString()))
                .andExpect(status().isForbidden());
        postAs(token, "/api/lessons", Map.of("groupId", group.getId(), "date", nextMonday().toString(),
                "startTime", "09:00", "endTime", "10:00")).andExpect(status().isForbidden());
    }

    @Test
    void manualLessonCannotOverlapTeacherOrRoom() throws Exception {
        String token = tokenFor("s_manager9", Role.MANAGER);
        Teacher teacher = teacher("s_teacher9", "Преподаватель");
        StudyGroup a = group("Группа S9a", teacher.getId());
        StudyGroup b = group("Группа S9b", teacher.getId());
        LocalDate monday = nextMonday();

        postAs(token, "/api/lessons", Map.of("groupId", a.getId(), "date", monday.toString(),
                "startTime", "15:00", "endTime", "16:00")).andExpect(status().isCreated());
        postAs(token, "/api/lessons", Map.of("groupId", b.getId(), "date", monday.toString(),
                "startTime", "15:30", "endTime", "16:30")).andExpect(status().isConflict());
        postAs(token, "/api/lessons", Map.of("groupId", b.getId(), "date", monday.toString(),
                "startTime", "16:00", "endTime", "17:00")).andExpect(status().isCreated());
    }

    @Test
    void teacherMarksStartedLessonHeldButNotFutureOne() throws Exception {
        Teacher teacher = teacher("s_teacher10", "Преподаватель");
        StudyGroup group = group("Группа S10", teacher.getId());
        Lesson started = lessonStartedAgo(group, teacher.getId(), 20);
        Lesson future = lessonAt(group, teacher.getId(), time.nowUtc().plusDays(2).withNano(0));
        String token = login("s_teacher10");

        putAs(token, "/api/lessons/" + started.getId() + "/status", Map.of("status", "HELD"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("HELD"));
        putAs(token, "/api/lessons/" + future.getId() + "/status", Map.of("status", "HELD"))
                .andExpect(status().isConflict());
    }

    @Test
    void teacherCannotCancelLesson() throws Exception {
        Teacher teacher = teacher("s_teacher11", "Преподаватель");
        StudyGroup group = group("Группа S11", teacher.getId());
        Lesson lesson = lessonStartedAgo(group, teacher.getId(), 20);
        String token = login("s_teacher11");

        putAs(token, "/api/lessons/" + lesson.getId() + "/status", Map.of("status", "CANCELLED"))
                .andExpect(status().isForbidden());
    }

    @Test
    void lessonWithoutTeacherCannotBeHeld() throws Exception {
        String token = tokenFor("s_manager12", Role.MANAGER);
        StudyGroup group = group("Группа S12", null);
        Lesson lesson = lessonStartedAgo(group, null, 20);

        putAs(token, "/api/lessons/" + lesson.getId() + "/status", Map.of("status", "HELD"))
                .andExpect(status().isConflict());
    }

    @Test
    void roomNamesAreUnique() throws Exception {
        String token = tokenFor("s_manager13", Role.MANAGER);

        postAs(token, "/api/rooms", Map.of("name", "Кабинет 1")).andExpect(status().isCreated());
        postAs(token, "/api/rooms", Map.of("name", "Кабинет 1")).andExpect(status().isConflict());
    }
}
