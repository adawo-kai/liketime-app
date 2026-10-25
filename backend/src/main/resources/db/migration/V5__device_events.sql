-- V5: события устройств распознавания. Таблица devices создана в V1.
-- Сырые события хранятся целиком (raw_payload), чтобы после изменения привязок или правил их можно
-- было разобрать заново.

-- Какой ученик скрывается за идентификатором человека на устройстве
CREATE TABLE device_persons (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    device_id          BIGINT       NOT NULL,
    external_person_id VARCHAR(100) NOT NULL,
    student_id         BIGINT       NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_device_persons_external UNIQUE (device_id, external_person_id),
    CONSTRAINT uk_device_persons_student UNIQUE (device_id, student_id),
    CONSTRAINT fk_device_persons_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE RESTRICT,
    CONSTRAINT fk_device_persons_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE RESTRICT
) ENGINE = InnoDB;

CREATE TABLE device_events (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    device_id          BIGINT        NOT NULL,
    -- Идентификатор события от устройства: по нему повторная отправка не создаёт дубль
    external_event_id  VARCHAR(100)  NOT NULL,
    external_person_id VARCHAR(100)  NULL,
    event_time         DATETIME(6)   NOT NULL,
    received_at        DATETIME(6)   NOT NULL,
    confidence         DECIMAL(5, 4) NULL,
    match_status       VARCHAR(30)   NOT NULL,
    student_id         BIGINT        NULL,
    lesson_id          BIGINT        NULL,
    note               VARCHAR(255)  NULL,
    raw_payload        LONGTEXT      NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_device_events_external UNIQUE (device_id, external_event_id),
    CONSTRAINT fk_device_events_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE RESTRICT,
    CONSTRAINT fk_device_events_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE RESTRICT,
    CONSTRAINT fk_device_events_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE RESTRICT,
    CONSTRAINT ck_device_events_status CHECK (match_status IN
        ('MATCHED', 'UNKNOWN_PERSON', 'LOW_CONFIDENCE', 'NO_LESSON', 'IGNORED')),
    CONSTRAINT ck_device_events_payload CHECK (raw_payload IS NULL OR JSON_VALID(raw_payload))
) ENGINE = InnoDB;

CREATE INDEX ix_device_events_time ON device_events (event_time);
CREATE INDEX ix_device_events_status ON device_events (match_status, event_time);
