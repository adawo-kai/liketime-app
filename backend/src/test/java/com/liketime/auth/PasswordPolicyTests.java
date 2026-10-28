package com.liketime.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PasswordPolicyTests {

    private static void rejects(String password, String messagePart) {
        assertThatThrownBy(() -> PasswordPolicy.check(password))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining(messagePart);
    }

    @Test
    void tooShortPasswordIsRejected() {
        rejects("123456789", "не короче 10");
        rejects("", "не короче 10");
        rejects(null, "не короче 10");
    }

    @Test
    void passwordOfTenCharactersIsAccepted() {
        assertThatCode(() -> PasswordPolicy.check("1234567890")).doesNotThrowAnyException();
    }

    @Test
    void passwordOfOnlySpacesIsRejected() {
        rejects("          ", "пробелов");
    }

    @Test
    void limitIs72BytesNotCharacters() {
        assertThatCode(() -> PasswordPolicy.check("a".repeat(72))).doesNotThrowAnyException();
        rejects("a".repeat(73), "слишком длинный");
        // русская буква занимает 2 байта: 36 букв ровно 72 байта, 37 уже много
        assertThatCode(() -> PasswordPolicy.check("ы".repeat(36))).doesNotThrowAnyException();
        rejects("ы".repeat(37), "слишком длинный");
    }
}
