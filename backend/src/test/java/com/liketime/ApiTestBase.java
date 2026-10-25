package com.liketime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liketime.auth.Role;
import com.liketime.auth.UserAccount;
import com.liketime.auth.UserAccountRepository;
import com.liketime.common.AppTime;
import com.liketime.group.GroupMembership;
import com.liketime.group.GroupMembershipRepository;
import com.liketime.group.StudyGroup;
import com.liketime.group.StudyGroupRepository;
import com.liketime.schedule.Lesson;
import com.liketime.schedule.LessonOrigin;
import com.liketime.schedule.LessonRepository;
import com.liketime.student.Student;
import com.liketime.student.StudentRepository;
import com.liketime.teacher.Teacher;
import com.liketime.teacher.TeacherRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Общая основа интеграционных тестов API. Каждый тест выполняется в транзакции, которая откатывается,
 * поэтому тестовые данные не остаются в базе. Тесты работают с настоящей MySQL (liketime_test).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class ApiTestBase {

    protected static final String PASSWORD = "Password123!";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected AppTime time;
    @Autowired
    protected UserAccountRepository users;
    @Autowired
    protected TeacherRepository teachers;
    @Autowired
    protected StudentRepository students;
    @Autowired
    protected StudyGroupRepository groups;
    @Autowired
    protected GroupMembershipRepository memberships;
    @Autowired
    protected LessonRepository lessons;

    protected UserAccount createUser(String login, Role role) {
        return users.saveAndFlush(new UserAccount(login, passwordEncoder.encode(PASSWORD), "Тест " + login, role));
    }

    /** Создаёт пользователя с ролью и возвращает его токен */
    protected String tokenFor(String login, Role role) throws Exception {
        createUser(login, role);
        return login(login);
    }

    protected String login(String login) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("login", login, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    /** Преподаватель с учётной записью. Возвращает преподавателя, токен получают через login(). */
    protected Teacher teacher(String login, String fullName) {
        UserAccount user = createUser(login, Role.TEACHER);
        return teachers.saveAndFlush(new Teacher(user.getId(), fullName, null));
    }

    protected Student student(String fullName) {
        return students.saveAndFlush(new Student(fullName, null, null, null));
    }

    protected StudyGroup group(String name, Long teacherId) {
        return groups.saveAndFlush(new StudyGroup(name, teacherId));
    }

    protected void enroll(StudyGroup group, Student student, LocalDate joinedOn) {
        memberships.saveAndFlush(new GroupMembership(group.getId(), student.getId(), joinedOn));
    }

    /** Занятие, которое началось minutesAgo минут назад и длится 60 минут */
    protected Lesson lessonStartedAgo(StudyGroup group, Long teacherId, long minutesAgo) {
        LocalDateTime start = time.nowUtc().minusMinutes(minutesAgo).withNano(0);
        return lessons.saveAndFlush(new Lesson(group.getId(), teacherId, null, null, start, start.plusMinutes(60),
                LessonOrigin.MANUAL, null));
    }

    /** Занятие в фиксированный момент (UTC) длительностью 60 минут */
    protected Lesson lessonAt(StudyGroup group, Long teacherId, LocalDateTime startUtc) {
        return lessons.saveAndFlush(new Lesson(group.getId(), teacherId, null, null, startUtc,
                startUtc.plusMinutes(60), LessonOrigin.MANUAL, null));
    }

    protected LocalDate localDateOf(Lesson lesson) {
        return time.localDate(lesson.getStartsAt());
    }

    protected LocalTime localTimeOf(Lesson lesson) {
        return time.localTime(lesson.getStartsAt());
    }

    // ----- запросы -----

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    protected ResultActions getAs(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", bearer(token)));
    }

    protected ResultActions postAs(String token, String url, Object body) throws Exception {
        return mvc.perform(post(url).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions putAs(String token, String url, Object body) throws Exception {
        return mvc.perform(put(url).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
