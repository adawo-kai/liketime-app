package com.liketime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReferenceSchemaTests {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void v1CreatesAllReferenceTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);

        assertThat(tables).contains("users", "teachers", "students", "subjects", "rooms", "devices");
    }

    @Test
    void userRoleIsRestrictedToKnownValues() {
        // Ошибка CHECK в MySQL не переводится Spring в DataIntegrityViolationException, поэтому ловим базовый тип
        assertThatThrownBy(() -> insertUser("bad_role", "BOSS"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_users_role");
    }

    @Test
    void userLoginIsUnique() {
        insertUser("dup", "ADMIN");

        assertThatThrownBy(() -> insertUser("dup", "MANAGER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void teacherCanBeLinkedToUserOnlyOnce() {
        insertUser("teacher_a", "TEACHER");
        Long userId = userId("teacher_a");
        jdbc.update("INSERT INTO teachers (user_id, full_name, status, created_at, updated_at) "
                + "VALUES (?, 'Иванов И.', 'ACTIVE', NOW(6), NOW(6))", userId);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO teachers (user_id, full_name, status, created_at, updated_at) "
                        + "VALUES (?, 'Петров П.', 'ACTIVE', NOW(6), NOW(6))", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void teacherStatusIsRestricted() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO teachers (full_name, status, created_at, updated_at) "
                        + "VALUES ('Сидоров С.', 'FIRED', NOW(6), NOW(6))"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_teachers_status");
    }

    @Test
    void deviceSerialNumberIsUnique() {
        jdbc.update("INSERT INTO devices (name, serial_number, status, created_at, updated_at) "
                + "VALUES ('Вход 1', 'SN-TEST-1', 'ACTIVE', NOW(6), NOW(6))");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO devices (name, serial_number, status, created_at, updated_at) "
                        + "VALUES ('Вход 2', 'SN-TEST-1', 'ACTIVE', NOW(6), NOW(6))"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertUser(String login, String role) {
        jdbc.update("INSERT INTO users (login, password_hash, full_name, role, enabled, created_at, updated_at) "
                + "VALUES (?, 'hash', 'Тест', ?, TRUE, NOW(6), NOW(6))", login, role);
    }

    private Long userId(String login) {
        return jdbc.queryForObject("SELECT id FROM users WHERE login = ?", Long.class, login);
    }
}
