package com.liketime.device;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.liketime.ApiTestBase;
import com.liketime.attendance.Attendance;
import com.liketime.attendance.AttendanceRepository;
import com.liketime.attendance.AttendanceSource;
import com.liketime.attendance.AttendanceStatus;
import com.liketime.auth.Role;
import com.liketime.group.StudyGroup;
import com.liketime.schedule.Lesson;
import com.liketime.schedule.LessonStatus;
import com.liketime.student.Student;
import com.liketime.teacher.Teacher;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class DeviceIngestIntegrationTests extends ApiTestBase {

    private static final String SERIAL = "SN-TEST-0001";
    private static final String KEY = "test-only-device-secret-key";

    @Autowired
    DeviceRepository devices;
    @Autowired
    DevicePersonRepository devicePersons;
    @Autowired
    DeviceEventRepository deviceEvents;
    @Autowired
    AttendanceRepository attendance;

    Device device;
    Teacher teacher;
    StudyGroup group;
    Student student;

    @BeforeEach
    void setUp() {
        device = devices.saveAndFlush(new Device("Вход", "DHI-ASI3213A-W", SERIAL, "Холл", null, "test-key"));
        teacher = teacher("d_teacher", "Преподаватель");
        group = group("Группа D", teacher.getId());
        student = student("Ученик Д");
        enroll(group, student, time.today().minusDays(30));
        devicePersons.saveAndFlush(new DevicePerson(device.getId(), "P-1", student.getId()));
    }

    private Map<String, Object> event(String id, String person, Instant at, Object confidence) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", id);
        body.put("personId", person);
        body.put("time", at.toString());
        if (confidence != null) {
            body.put("confidence", confidence);
        }
        return body;
    }

    private ResultActions send(String serial, String key, Object body) throws Exception {
        var request = post("/api/ingest/devices/" + serial + "/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body instanceof String s ? s : json(body));
        if (key != null) {
            request.header("X-Device-Key", key);
        }
        return mvc.perform(request);
    }

    private Attendance attendanceOf(Lesson lesson) {
        return attendance.findByLessonIdAndStudentId(lesson.getId(), student.getId()).orElse(null);
    }

    @Test
    void recognisedStudentIsMarkedPresentAndLessonBecomesHeld() throws Exception {
        Lesson lesson = lessonStartedAgo(group, teacher.getId(), 3);

        send(SERIAL, KEY, event("e1", "P-1", Instant.now(), 0.95))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.lessonId").value(lesson.getId()));

        Attendance mark = attendanceOf(lesson);
        assertThat(mark.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(mark.getSource()).isEqualTo(AttendanceSource.DEVICE);
        assertThat(lessons.findById(lesson.getId()).orElseThrow().getStatus()).isEqualTo(LessonStatus.HELD);
    }

    @Test
    void arrivalLongAfterStartIsMarkedLate() throws Exception {
        Lesson lesson = lessonStartedAgo(group, teacher.getId(), 25);

        send(SERIAL, KEY, event("e1", "P-1", Instant.now(), 0.95)).andExpect(status().isOk());

        assertThat(attendanceOf(lesson).getStatus()).isEqualTo(AttendanceStatus.LATE);
    }

    @Test
    void repeatedEventIsAcceptedOnceOnly() throws Exception {
        Lesson lesson = lessonStartedAgo(group, teacher.getId(), 3);
        Map<String, Object> body = event("dup-1", "P-1", Instant.now(), 0.95);

        send(SERIAL, KEY, body).andExpect(jsonPath("$.duplicate").value(false));
        send(SERIAL, KEY, body).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));

        assertThat(deviceEvents.findAll()).filteredOn(e -> e.getExternalEventId().equals("dup-1")).hasSize(1);
        assertThat(attendance.findByLessonId(lesson.getId())).hasSize(1);
    }

    @Test
    void earlierEventFixesStatusWhenEventsArriveOutOfOrder() throws Exception {
        Lesson lesson = lessonStartedAgo(group, teacher.getId(), 20);

        // Сначала пришло позднее событие (опоздал), затем более раннее (на самом деле пришёл вовремя)
        send(SERIAL, KEY, event("late", "P-1", Instant.now().minus(2, ChronoUnit.MINUTES), 0.95)).andExpect(status().isOk());
        assertThat(attendanceOf(lesson).getStatus()).isEqualTo(AttendanceStatus.LATE);
        send(SERIAL, KEY, event("early", "P-1", Instant.now().minus(15, ChronoUnit.MINUTES), 0.95))
                .andExpect(status().isOk());

        assertThat(attendanceOf(lesson).getStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    void manualMarkIsNeverOverwrittenByDevice() throws Exception {
        Lesson lesson = lessonStartedAgo(group, teacher.getId(), 5);
        String token = tokenFor("d_manager", Role.MANAGER);
        putAs(token, "/api/lessons/" + lesson.getId() + "/attendance/" + student.getId(),
                Map.of("status", "ABSENT", "note", "ушёл раньше")).andExpect(status().isOk());

        send(SERIAL, KEY, event("e1", "P-1", Instant.now(), 0.95))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.note").value("Есть ручная отметка, она сохранена"));

        assertThat(attendanceOf(lesson).getStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(attendanceOf(lesson).getSource()).isEqualTo(AttendanceSource.MANUAL);
    }

    @Test
    void unknownFaceIsKeptAndCanBeReprocessedAfterLinking() throws Exception {
        Lesson lesson = lessonStartedAgo(group, teacher.getId(), 3);
        String token = tokenFor("d_manager2", Role.MANAGER);
        Student newcomer = student("Новичок");
        enroll(group, newcomer, time.today().minusDays(1));

        send(SERIAL, KEY, event("u1", "P-NEW", Instant.now(), 0.97))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNKNOWN_PERSON"));
        getAs(token, "/api/devices/" + device.getId() + "/unmatched")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalPersonId").value("P-NEW"));

        putAs(token, "/api/devices/" + device.getId() + "/persons",
                Map.of("externalPersonId", "P-NEW", "studentId", newcomer.getId())).andExpect(status().isOk());
        postAs(token, "/api/device-events/reprocess",
                Map.of("from", time.today().toString(), "to", time.today().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byStatus.MATCHED").value(1));

        assertThat(attendance.findByLessonIdAndStudentId(lesson.getId(), newcomer.getId())).isPresent();
    }

    @Test
    void lowConfidenceDoesNotMarkAttendance() throws Exception {
        Lesson lesson = lessonStartedAgo(group, teacher.getId(), 3);

        send(SERIAL, KEY, event("low", "P-1", Instant.now(), 0.5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOW_CONFIDENCE"));

        assertThat(attendanceOf(lesson)).isNull();
    }

    @Test
    void confidenceInPercentIsUnderstood() throws Exception {
        Lesson lesson = lessonStartedAgo(group, teacher.getId(), 3);

        send(SERIAL, KEY, event("pct", "P-1", Instant.now(), 95)).andExpect(jsonPath("$.status").value("MATCHED"));
        send(SERIAL, KEY, event("pct-low", "P-1", Instant.now(), 40)).andExpect(jsonPath("$.status").value("LOW_CONFIDENCE"));

        assertThat(attendanceOf(lesson)).isNotNull();
    }

    @Test
    void eventWithoutLessonIsStoredAsNoLesson() throws Exception {
        send(SERIAL, KEY, event("nl", "P-1", Instant.now(), 0.95))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_LESSON"));
    }

    @Test
    void nearestLessonIsChosenWhenStudentHasTwo() throws Exception {
        StudyGroup other = group("Группа D2", null);
        enroll(other, student, time.today().minusDays(30));
        lessonStartedAgo(group, teacher.getId(), 50);
        Lesson upcoming = lessonAt(other, null, time.nowUtc().plusMinutes(20).withNano(0));

        send(SERIAL, KEY, event("two", "P-1", Instant.now(), 0.95))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lessonId").value(upcoming.getId()));
    }

    @Test
    void wrongKeyUnknownDeviceAndDisabledDeviceAreRejected() throws Exception {
        Map<String, Object> body = event("e1", "P-1", Instant.now(), 0.95);

        send(SERIAL, "wrong-key", body).andExpect(status().isUnauthorized());
        send(SERIAL, null, body).andExpect(status().isUnauthorized());
        send("SN-UNKNOWN", KEY, body).andExpect(status().isUnauthorized());

        device.setStatus(DeviceStatus.DISABLED);
        devices.saveAndFlush(device);
        send(SERIAL, KEY, body).andExpect(status().isForbidden());
        assertThat(deviceEvents.findAll()).isEmpty();
    }

    @Test
    void malformedEventsAreRejected() throws Exception {
        send(SERIAL, KEY, "это не json").andExpect(status().isBadRequest());
        send(SERIAL, KEY, Map.of("personId", "P-1", "time", Instant.now().toString())).andExpect(status().isBadRequest());
        send(SERIAL, KEY, Map.of("eventId", "x", "personId", "P-1", "time", "вчера")).andExpect(status().isBadRequest());
        send(SERIAL, KEY, event("future", "P-1", Instant.now().plus(2, ChronoUnit.HOURS), 0.95))
                .andExpect(status().isBadRequest());
    }

    @Test
    void heartbeatUpdatesLastSeen() throws Exception {
        mvc.perform(post("/api/ingest/devices/" + SERIAL + "/heartbeat").header("X-Device-Key", KEY))
                .andExpect(status().isNoContent());

        assertThat(devices.findById(device.getId()).orElseThrow().getLastSeenAt()).isNotNull();
    }

    @Test
    void deviceSettingsAreAdminOnlyAndEventLogIsStaffOnly() throws Exception {
        String manager = tokenFor("d_manager3", Role.MANAGER);
        String admin = tokenFor("d_admin3", Role.ADMIN);
        Teacher t = teacher("d_teacher3", "Другой");
        String teacherToken = login("d_teacher3");
        Map<String, Object> newDevice = Map.of("name", "Второй вход", "serialNumber", "SN-2", "secretRef", "test-key");

        postAs(manager, "/api/devices", newDevice).andExpect(status().isForbidden());
        postAs(admin, "/api/devices", newDevice).andExpect(status().isCreated())
                .andExpect(jsonPath("$.secretConfigured").value(true));
        getAs(manager, "/api/devices").andExpect(status().isOk());
        getAs(teacherToken, "/api/devices").andExpect(status().isForbidden());
        getAs(teacherToken, "/api/device-events?from=" + time.today() + "&to=" + time.today())
                .andExpect(status().isForbidden());
        assertThat(t.getId()).isNotNull();
    }

    @Test
    void deviceResponseNeverExposesSecretValue() throws Exception {
        String admin = tokenFor("d_admin4", Role.ADMIN);

        String body = getAs(admin, "/api/devices").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(KEY);
    }
}
