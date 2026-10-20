-- V2: группы и состав групп. Таблицы students и teachers созданы в V1.
-- Имя "groups" зарезервировано в MySQL 8, поэтому таблица называется study_groups.

CREATE TABLE study_groups (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    teacher_id BIGINT       NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_study_groups_name UNIQUE (name),
    CONSTRAINT fk_study_groups_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE RESTRICT,
    CONSTRAINT ck_study_groups_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE = InnoDB;

-- Состав группы с историей: ученик вступает (joined_on) и выходит (left_on), строки не удаляются,
-- чтобы посещаемость и зарплата за прошлые периоды считались по тому составу, который был тогда.
CREATE TABLE group_students (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    group_id   BIGINT      NOT NULL,
    student_id BIGINT      NOT NULL,
    joined_on  DATE        NOT NULL,
    left_on    DATE        NULL,
    -- 1, пока ученик в группе, иначе NULL: уникальный индекс не даёт добавить ученика в группу дважды
    active_key TINYINT GENERATED ALWAYS AS (IF(left_on IS NULL, 1, NULL)) STORED,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_group_students_active UNIQUE (group_id, student_id, active_key),
    CONSTRAINT fk_group_students_group FOREIGN KEY (group_id) REFERENCES study_groups (id) ON DELETE RESTRICT,
    CONSTRAINT fk_group_students_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE RESTRICT,
    CONSTRAINT ck_group_students_dates CHECK (left_on IS NULL OR left_on >= joined_on)
) ENGINE = InnoDB;

CREATE INDEX ix_group_students_student ON group_students (student_id);
CREATE INDEX ix_study_groups_teacher ON study_groups (teacher_id);
