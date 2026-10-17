package com.liketime.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class AuthIntegrationTests {

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
    void loginReturnsBearerToken() throws Exception {
        createUser("t_login_ok", Role.TEACHER, true);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("t_login_ok", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(8 * 60 * 60));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        createUser("t_wrong_pw", Role.TEACHER, true);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("t_wrong_pw", "NotThePassword1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownLoginIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("t_nobody", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledUserCannotLogin() throws Exception {
        createUser("t_disabled_login", Role.TEACHER, false);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("t_disabled_login", PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointRequiresToken() throws Exception {
        mvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teacherCannotManageUsers() throws Exception {
        createUser("t_teacher_role", Role.TEACHER, true);
        String token = login("t_teacher_role");

        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void meReturnsCurrentUserWithRole() throws Exception {
        createUser("t_me", Role.MANAGER, true);
        String token = login("t_me");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login").value("t_me"))
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void adminCreatesUserWithHashedPassword() throws Exception {
        createUser("t_admin_create", Role.ADMIN, true);
        String token = login("t_admin_create");

        mvc.perform(post("/api/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "login", "t_new_teacher",
                                "password", PASSWORD,
                                "fullName", "Новый преподаватель",
                                "role", "TEACHER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.login").value("t_new_teacher"));

        UserAccount created = users.findByLogin("t_new_teacher").orElseThrow();
        assertThat(created.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, created.getPasswordHash())).isTrue();
    }

    @Test
    void duplicateLoginIsConflict() throws Exception {
        createUser("t_admin_dup", Role.ADMIN, true);
        createUser("t_taken", Role.TEACHER, true);
        String token = login("t_admin_dup");

        mvc.perform(post("/api/users").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "login", "t_taken",
                                "password", PASSWORD,
                                "fullName", "Дубль",
                                "role", "MANAGER"))))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCannotDisableOwnAccount() throws Exception {
        UserAccount admin = createUser("t_admin_self", Role.ADMIN, true);
        String token = login("t_admin_self");

        mvc.perform(patch("/api/users/" + admin.getId()).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    void tokenStopsWorkingWhenUserIsDisabled() throws Exception {
        UserAccount admin = createUser("t_admin_disable", Role.ADMIN, true);
        UserAccount teacher = createUser("t_to_disable", Role.TEACHER, true);
        String adminToken = login("t_admin_disable");
        String teacherToken = login("t_to_disable");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/users/" + teacher.getId()).header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isUnauthorized());
        assertThat(admin.isEnabled()).isTrue();
    }

    @Test
    void adminResetsPassword() throws Exception {
        createUser("t_admin_reset", Role.ADMIN, true);
        UserAccount target = createUser("t_reset_target", Role.TEACHER, true);
        String token = login("t_admin_reset");

        mvc.perform(put("/api/users/" + target.getId() + "/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"NewPassword456!\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("t_reset_target", "NewPassword456!")))
                .andExpect(status().isOk());
    }

    private UserAccount createUser(String login, Role role, boolean enabled) {
        UserAccount user = new UserAccount(login, passwordEncoder.encode(PASSWORD), "Тест " + login, role);
        user.setEnabled(enabled);
        return users.saveAndFlush(user);
    }

    private String login(String login) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(login, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String credentials(String login, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("login", login, "password", password));
    }
}
