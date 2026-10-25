-- V3: расписание, занятия и журнал аудита.
-- Время начала и конца занятия хранится в UTC. Шаблоны расписания (schedule_slots) заданы
-- в местном времени школы (Asia/Bishkek), перевод в UTC делает приложение при создании занятий.

CREATE TABLE schedule_slots (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    group_id   BIGINT      NOT NULL,
    -- День недели по ISO: 1 = понедельник ... 7 = воскресенье
    weekday    INT         NOT NULL,
    start_time TIME        NOT NULL,
    end_time   TIME        NOT NULL,
    room_id    BIGINT      NULL,
    -- Шаблон не удаляется, а отключается: по нему уже могли быть созданы занятия
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_schedule_slots_group_time UNIQUE (group_id, weekday, start_time),
    CONSTRAINT fk_schedule_slots_group FOREIGN KEY (group_id) REFERENCES study_groups (id) ON DELETE RESTRICT,
    CONSTRAINT fk_schedule_slots_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE RESTRICT,
    CONSTRAINT ck_schedule_slots_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT ck_schedule_slots_time CHECK (end_time > start_time)
) ENGINE = InnoDB;

CREATE TABLE lessons (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    group_id   BIGINT       NOT NULL,
    -- Фактический преподаватель занятия (может отличаться от преподавателя группы при замене)
    teacher_id BIGINT       NULL,
    room_id    BIGINT       NULL,
    slot_id    BIGINT       NULL,
    starts_at  DATETIME(6)  NOT NULL,
    ends_at    DATETIME(6)  NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',
    origin     VARCHAR(20)  NOT NULL,
    note       VARCHAR(255) NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_lessons_group_start UNIQUE (group_id, starts_at),
    CONSTRAINT fk_lessons_group FOREIGN KEY (group_id) REFERENCES study_groups (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lessons_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lessons_room FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE RESTRICT,
    CONSTRAINT fk_lessons_slot FOREIGN KEY (slot_id) REFERENCES schedule_slots (id) ON DELETE RESTRICT,
    CONSTRAINT ck_lessons_time CHECK (ends_at > starts_at),
    CONSTRAINT ck_lessons_status CHECK (status IN ('PLANNED', 'HELD', 'CANCELLED')),
    CONSTRAINT ck_lessons_origin CHECK (origin IN ('GENERATED', 'MANUAL'))
) ENGINE = InnoDB;

CREATE INDEX ix_lessons_starts_at ON lessons (starts_at);
CREATE INDEX ix_lessons_teacher_start ON lessons (teacher_id, starts_at);
CREATE INDEX ix_lessons_room_start ON lessons (room_id, starts_at);

-- Журнал аудита: кто, когда и что изменил. details хранит JSON с подробностями (до и после).
CREATE TABLE audit_log (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    actor      VARCHAR(64)  NULL,
    action     VARCHAR(60)  NOT NULL,
    entity     VARCHAR(60)  NOT NULL,
    entity_id  BIGINT       NULL,
    details    LONGTEXT     NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_audit_details CHECK (details IS NULL OR JSON_VALID(details))
) ENGINE = InnoDB;

CREATE INDEX ix_audit_created ON audit_log (created_at);
CREATE INDEX ix_audit_entity ON audit_log (entity, entity_id);
