-- V1: справочники и пользователи.
-- Все даты хранятся в UTC (DATETIME(6)), удаление мягкое через status.

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    login         VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_login UNIQUE (login),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'MANAGER', 'TEACHER'))
) ENGINE = InnoDB;

CREATE TABLE teachers (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NULL,
    full_name  VARCHAR(150) NOT NULL,
    phone      VARCHAR(20)  NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_teachers_user UNIQUE (user_id),
    CONSTRAINT fk_teachers_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_teachers_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB;

CREATE TABLE students (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    full_name    VARCHAR(150) NOT NULL,
    phone        VARCHAR(20)  NULL,
    parent_name  VARCHAR(150) NULL,
    parent_phone VARCHAR(20)  NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_students_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB;

CREATE TABLE subjects (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_subjects_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE rooms (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_rooms_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE devices (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    model         VARCHAR(100) NULL,
    serial_number VARCHAR(100) NOT NULL,
    location      VARCHAR(150) NULL,
    host          VARCHAR(255) NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    last_seen_at  DATETIME(6)  NULL,
    -- Ссылка на секрет (имя переменной окружения или ключа), а не сам секрет
    secret_ref    VARCHAR(100) NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_devices_serial UNIQUE (serial_number),
    CONSTRAINT ck_devices_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE = InnoDB;
