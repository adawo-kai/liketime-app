package com.liketime.auth;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Требования к новому паролю: не короче 10 символов и не длиннее 72 байт.
 * Предел в 72 байта идёт от BCrypt, который иначе молча обрезал бы пароль или падал; русская буква занимает
 * 2 байта, поэтому по-русски влезает около 36 букв.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void check(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw bad("Пароль должен быть не короче " + MIN_LENGTH + " символов");
        }
        if (password.isBlank()) {
            throw bad("Пароль не может состоять из одних пробелов");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw bad("Пароль слишком длинный: не более " + MAX_BYTES + " байт (около 72 латинских или 36 русских букв)");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
