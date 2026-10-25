-- V4: посещаемость. Одна запись на ученика в занятии.
-- source = DEVICE ставит устройство распознавания, MANUAL - человек (ручная отметка важнее устройства).

CREATE TABLE attendance (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    lesson_id   BIGINT       NOT NULL,
    student_id  BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    source      VARCHAR(20)  NOT NULL,
    -- Время первого прихода по устройству (UTC)
    check_in_at DATETIME(6)  NULL,
    recorded_by VARCHAR(64)  NULL,
    note        VARCHAR(255) NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_attendance_lesson_student UNIQUE (lesson_id, student_id),
    CONSTRAINT fk_attendance_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE RESTRICT,
    CONSTRAINT fk_attendance_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE RESTRICT,
    CONSTRAINT ck_attendance_status CHECK (status IN ('PRESENT', 'LATE', 'ABSENT', 'EXCUSED')),
    CONSTRAINT ck_attendance_source CHECK (source IN ('MANUAL', 'DEVICE'))
) ENGINE = InnoDB;

CREATE INDEX ix_attendance_student ON attendance (student_id);
