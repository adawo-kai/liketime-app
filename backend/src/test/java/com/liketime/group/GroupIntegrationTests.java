package com.liketime.group;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liketime.auth.Role;
import com.liketime.auth.UserAccount;
import com.liketime.auth.UserAccountRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GroupIntegrationTests {

    private static final String PASSWORD = "Password123!";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserAccountRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void managerCreatesStudentAndFindsItBySearch() throws Exception {
        String token = tokenFor("g_manager1", Role.MANAGER);

        long id = create(token, "/api/students", Map.of("fullName", "Иванов Пётр", "phone", "+996700000001"));

        mvc.perform(get("/api/students").param("q", "иванов").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")].status").value("ACTIVE"));
    }

    @Test
    void studentNameIsRequired() throws Exception {
        String token = tokenFor("g_manager2", Role.MANAGER);

        mvc.perform(post("/api/students").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void teacherRoleCannotUseStudentsApi() throws Exception {
        String token = tokenFor("g_teacher_students", Role.TEACHER);

        mvc.perform(get("/api/students").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCanOnlyBeLinkedToTeacherRoleUser() throws Exception {
        String token = tokenFor("g_admin1", Role.ADMIN);
        UserAccount manager = createUser("g_not_teacher", Role.MANAGER);

        mvc.perform(post("/api/teachers").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Сидорова А.", "userId", manager.getId()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void userCannotBeLinkedToTwoTeachers() throws Exception {
        String token = tokenFor("g_admin2", Role.ADMIN);
        UserAccount user = createUser("g_teacher_user", Role.TEACHER);
        create(token, "/api/teachers", Map.of("fullName", "Первый", "userId", user.getId()));

        mvc.perform(post("/api/teachers").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Второй", "userId", user.getId()))))
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateGroupNameIsConflict() throws Exception {
        String token = tokenFor("g_manager3", Role.MANAGER);
        create(token, "/api/groups", Map.of("name", "Английский A1"));

        mvc.perform(post("/api/groups").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "Английский A1"))))
                .andExpect(status().isConflict());
    }

    @Test
    void studentJoinsAndLeavesGroupWithHistoryKept() throws Exception {
        String token = tokenFor("g_manager4", Role.MANAGER);
        long groupId = create(token, "/api/groups", Map.of("name", "Математика 5"));
        long studentId = create(token, "/api/students", Map.of("fullName", "Ученик История"));

        mvc.perform(post("/api/groups/" + groupId + "/students").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("studentId", studentId, "joinedOn", "2026-09-01"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.joinedOn").value("2026-09-01"));

        mvc.perform(get("/api/groups/" + groupId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.activeStudents").value(1));

        mvc.perform(delete("/api/groups/" + groupId + "/students/" + studentId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/groups/" + groupId + "/students").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/groups/" + groupId + "/students").param("activeOnly", "false")
                        .header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].leftOn").isNotEmpty());
    }

    @Test
    void studentCannotBeAddedToSameGroupTwice() throws Exception {
        String token = tokenFor("g_manager5", Role.MANAGER);
        long groupId = create(token, "/api/groups", Map.of("name", "Физика 7"));
        long studentId = create(token, "/api/students", Map.of("fullName", "Ученик Дубль"));
        String body = json(Map.of("studentId", studentId));

        mvc.perform(post("/api/groups/" + groupId + "/students").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/groups/" + groupId + "/students").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
    }

    @Test
    void deactivatingStudentRemovesThemFromGroups() throws Exception {
        String token = tokenFor("g_manager6", Role.MANAGER);
        long groupId = create(token, "/api/groups", Map.of("name", "Химия 8"));
        long studentId = create(token, "/api/students", Map.of("fullName", "Ученик Уходит"));
        mvc.perform(post("/api/groups/" + groupId + "/students").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("studentId", studentId))));

        mvc.perform(delete("/api/students/" + studentId).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/groups/" + groupId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.activeStudents").value(0));
        mvc.perform(post("/api/groups/" + groupId + "/students").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("studentId", studentId))))
                .andExpect(status().isConflict());
    }

    @Test
    void teacherWithActiveGroupCannotBeDeactivated() throws Exception {
        String token = tokenFor("g_admin3", Role.ADMIN);
        long teacherId = create(token, "/api/teachers", Map.of("fullName", "Занятой Преподаватель"));
        create(token, "/api/groups", Map.of("name", "Биология 9", "teacherId", teacherId));

        mvc.perform(delete("/api/teachers/" + teacherId).header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void teacherSeesOnlyOwnGroups() throws Exception {
        String adminToken = tokenFor("g_admin4", Role.ADMIN);
        UserAccount teacherUser = createUser("g_teacher_own", Role.TEACHER);
        long ownTeacher = create(adminToken, "/api/teachers",
                Map.of("fullName", "Свой Преподаватель", "userId", teacherUser.getId()));
        long otherTeacher = create(adminToken, "/api/teachers", Map.of("fullName", "Чужой Преподаватель"));
        long ownGroup = create(adminToken, "/api/groups", Map.of("name", "Своя группа", "teacherId", ownTeacher));
        long otherGroup = create(adminToken, "/api/groups",
                Map.of("name", "Чужая группа", "teacherId", otherTeacher));
        String teacherToken = login("g_teacher_own");

        mvc.perform(get("/api/groups").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ownGroup));
        mvc.perform(get("/api/groups/" + otherGroup).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/groups/" + otherGroup + "/students").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherCannotChangeGroups() throws Exception {
        String adminToken = tokenFor("g_admin5", Role.ADMIN);
        long groupId = create(adminToken, "/api/groups", Map.of("name", "Только чтение"));
        String teacherToken = tokenFor("g_teacher_ro", Role.TEACHER);

        mvc.perform(put("/api/groups/" + groupId).header("Authorization", bearer(teacherToken))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "Взлом"))))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/groups/" + groupId).header("Authorization", bearer(teacherToken)))
                .andExpect(status().isForbidden());
    }

    private long create(String token, String url, Map<String, ?> body) throws Exception {
        String response = mvc.perform(post(url).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.get("id").asLong();
    }

    private UserAccount createUser(String login, Role role) {
        return users.saveAndFlush(new UserAccount(login, passwordEncoder.encode(PASSWORD), "Тест " + login, role));
    }

    private String tokenFor(String login, Role role) throws Exception {
        createUser(login, role);
        return login(login);
    }

    private String login(String login) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("login", login, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
