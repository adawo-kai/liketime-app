package com.liketime.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.liketime.ApiTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Блокировка входа, аудит управления пользователями и раздача сайта. */
class HardeningIntegrationTests extends ApiTestBase {

    private ResultActions loginFrom(String ip, String login, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("login", login, "password", password)))
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                }));
    }

    @Test
    void sixthAttemptIsBlockedEvenWithTheCorrectPassword() throws Exception {
        createUser("h_victim", Role.TEACHER);

        for (int i = 0; i < 5; i++) {
            loginFrom("10.20.30.1", "h_victim", "wrong-password").andExpect(status().isUnauthorized());
        }

        loginFrom("10.20.30.1", "h_victim", PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Повторите через")));
    }

    @Test
    void blockedAttackerDoesNotLockOutTheRealUserFromAnotherAddress() throws Exception {
        createUser("h_victim2", Role.TEACHER);
        for (int i = 0; i < 5; i++) {
            loginFrom("10.20.30.2", "h_victim2", "wrong-password").andExpect(status().isUnauthorized());
        }

        loginFrom("10.20.30.3", "h_victim2", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unknownLoginsAreCountedToo() throws Exception {
        for (int i = 0; i < 5; i++) {
            loginFrom("10.20.30.4", "h_nobody", "whatever-123").andExpect(status().isUnauthorized());
        }

        loginFrom("10.20.30.4", "h_nobody", "whatever-123").andExpect(status().isTooManyRequests());
    }

    @Test
    void userManagementIsAudited() throws Exception {
        String admin = tokenFor("h_admin", Role.ADMIN);
        long id = read(postAs(admin, "/api/users", Map.of("login", "h_new_user", "password", PASSWORD,
                "fullName", "Новый", "role", "TEACHER")).andExpect(status().isCreated())).get("id").asLong();
        mvc.perform(patch("/api/users/" + id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MANAGER\"}"))
                .andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/users/" + id + "/password").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"AnotherPassword1!\"}"))
                .andExpect(status().isNoContent());

        String log = getAs(admin, "/api/audit?entity=user&entityId=" + id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.action=='USER_CREATE')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action=='USER_UPDATE')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.action=='USER_PASSWORD_RESET')]").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(log)
                .doesNotContain("AnotherPassword1!")
                .doesNotContain(PASSWORD);
    }

    // ----- раздача сайта (в тестах лежит заглушка static/index.html) -----

    @Test
    void siteAddressesServeIndexWithoutLogin() throws Exception {
        // Корень "/" Spring пересылает на index.html внутри сервера; MockMvc такие пересылки не выполняет,
        // поэтому тело проверяем у обычных адресов сайта, а корень проверяем в живом запуске
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/groups")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"root\"")));
        mvc.perform(get("/salary/5")).andExpect(status().isOk());
    }

    @Test
    void apiAndActuatorStayProtectedAndDoNotFallBackToTheSite() throws Exception {
        String admin = tokenFor("h_admin2", Role.ADMIN);

        mvc.perform(get("/api/students")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isUnauthorized());
        getAs(admin, "/api/does-not-exist").andExpect(status().isNotFound());
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void missingAssetFileIsNotFoundInsteadOfTheSite() throws Exception {
        mvc.perform(get("/assets/missing-file.js")).andExpect(status().isNotFound());
    }
}
