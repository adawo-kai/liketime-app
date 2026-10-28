package com.liketime.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.liketime.ApiTestBase;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Смена своего пароля и закрытие старых сеансов после смены или сброса пароля. */
class PasswordChangeIntegrationTests extends ApiTestBase {

    private static final String NEW_PASSWORD = "NewPassw0rd-123";

    private ResultActions change(String token, String current, String next) throws Exception {
        return putAs(token, "/api/auth/password", Map.of("currentPassword", current, "newPassword", next));
    }

    private ResultActions loginAttempt(String login, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("login", login, "password", password))));
    }

    private ResultActions me(String token) throws Exception {
        return mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)));
    }

    @Test
    void passwordIsChangedAndOldOneStopsWorking() throws Exception {
        String token = tokenFor("pw_user1", Role.MANAGER);

        change(token, PASSWORD, NEW_PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());

        loginAttempt("pw_user1", PASSWORD).andExpect(status().isUnauthorized());
        loginAttempt("pw_user1", NEW_PASSWORD).andExpect(status().isOk());
    }

    @Test
    void sessionContinuesWithTheNewTokenButOldTokensAreRevoked() throws Exception {
        String oldToken = tokenFor("pw_user2", Role.MANAGER);
        // Время в токене хранится с точностью до секунды, поэтому ждём, чтобы старый токен был заведомо раньше смены
        Thread.sleep(1100);

        String body = change(oldToken, PASSWORD, NEW_PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newToken = objectMapper.readTree(body).get("accessToken").asText();

        me(oldToken).andExpect(status().isUnauthorized());
        me(newToken).andExpect(status().isOk()).andExpect(jsonPath("$.login").value("pw_user2"));
    }

    @Test
    void wrongCurrentPasswordIsRejectedWithoutSigningTheUserOut() throws Exception {
        String token = tokenFor("pw_user3", Role.MANAGER);

        // 400, а не 401: 401 сайт понимает как «сеанс закончился» и выходит из системы
        change(token, "NotMyPassword-1", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Текущий пароль указан неверно"));

        loginAttempt("pw_user3", PASSWORD).andExpect(status().isOk());
        me(token).andExpect(status().isOk());
    }

    @Test
    void newPasswordMustBeAcceptable() throws Exception {
        String token = tokenFor("pw_user4", Role.MANAGER);

        change(token, PASSWORD, PASSWORD).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Новый пароль должен отличаться от текущего"));
        change(token, PASSWORD, "short").andExpect(status().isBadRequest());
        change(token, PASSWORD, "           ").andExpect(status().isBadRequest());
        // 40 русских букв это 80 байт: раньше такой пароль мог привести к ошибке сервера
        change(token, PASSWORD, "ы".repeat(40)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("слишком длинный")));

        loginAttempt("pw_user4", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void guessingTheCurrentPasswordIsLimitedLikeLoginAttempts() throws Exception {
        String token = tokenFor("pw_user5", Role.MANAGER);

        for (int i = 0; i < 5; i++) {
            change(token, "wrong-password-" + i, NEW_PASSWORD).andExpect(status().isBadRequest());
        }

        change(token, PASSWORD, NEW_PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void anonymousUserCannotChangeAnything() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", PASSWORD, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changeIsAuditedWithoutPasswords() throws Exception {
        UserAccount user = createUser("pw_user6", Role.TEACHER);
        String token = login("pw_user6");
        String admin = tokenFor("pw_admin6", Role.ADMIN);

        change(token, PASSWORD, NEW_PASSWORD).andExpect(status().isOk());

        String log = getAs(admin, "/api/audit?entity=user&entityId=" + user.getId())
                .andExpect(jsonPath("$[?(@.action=='USER_PASSWORD_CHANGE')]").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(log).doesNotContain(PASSWORD).doesNotContain(NEW_PASSWORD);
    }

    @Test
    void adminResetClosesTheTargetsOldSessions() throws Exception {
        UserAccount target = createUser("pw_target", Role.TEACHER);
        String targetToken = login("pw_target");
        String admin = tokenFor("pw_admin7", Role.ADMIN);
        Thread.sleep(1100);

        putAs(admin, "/api/users/" + target.getId() + "/password", Map.of("password", "ResetByAdmin-77"))
                .andExpect(status().isNoContent());

        me(targetToken).andExpect(status().isUnauthorized());
        loginAttempt("pw_target", "ResetByAdmin-77").andExpect(status().isOk());
    }

    @Test
    void adminCannotSetAPasswordTooLongForBcrypt() throws Exception {
        UserAccount target = createUser("pw_target2", Role.TEACHER);
        String admin = tokenFor("pw_admin8", Role.ADMIN);

        putAs(admin, "/api/users/" + target.getId() + "/password", Map.of("password", "ы".repeat(40)))
                .andExpect(status().isBadRequest());
        postAs(admin, "/api/users", Map.of("login", "pw_new_user", "password", "ы".repeat(40),
                "fullName", "Новый", "role", "TEACHER")).andExpect(status().isBadRequest());
    }
}
