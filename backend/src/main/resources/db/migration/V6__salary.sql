-- V6: правила зарплаты, зарплатные периоды, начисления и ручные корректировки.
-- Деньги хранятся как DECIMAL(12,2). Сейчас единственный тип правила - PER_VISIT: фиксированная
-- сумма за один приход одного ученика на проведённое занятие.

CREATE TABLE salary_rules (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    -- Область действия: пусто = для всех. Чем уже область (группа, преподаватель), тем выше приоритет.
    teacher_id     BIGINT         NULL,
    group_id       BIGINT         NULL,
    rule_type      VARCHAR(20)    NOT NULL DEFAULT 'PER_VISIT',
    amount         DECIMAL(12, 2) NOT NULL,
    effective_from DATE           NOT NULL,
    effective_to   DATE           NULL,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    comment        VARCHAR(255)   NULL,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_salary_rules_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_salary_rules_group FOREIGN KEY (group_id) REFERENCES study_groups (id) ON DELETE RESTRICT,
    CONSTRAINT ck_salary_rules_type CHECK (rule_type IN ('PER_VISIT')),
    CONSTRAINT ck_salary_rules_amount CHECK (amount >= 0),
    CONSTRAINT ck_salary_rules_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE = InnoDB;

CREATE TABLE salary_periods (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    starts_on     DATE        NOT NULL,
    ends_on       DATE        NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    calculated_at DATETIME(6) NULL,
    approved_at   DATETIME(6) NULL,
    approved_by   VARCHAR(64) NULL,
    paid_at       DATETIME(6) NULL,
    paid_by       VARCHAR(64) NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_salary_periods_start UNIQUE (starts_on),
    CONSTRAINT ck_salary_periods_dates CHECK (ends_on >= starts_on),
    CONSTRAINT ck_salary_periods_status CHECK (status IN ('OPEN', 'CALCULATED', 'APPROVED', 'PAID'))
) ENGINE = InnoDB;

-- Одна строка начисления на занятие. rule_snapshot хранит условия правила на момент расчёта,
-- поэтому позднее изменение правила не меняет уже рассчитанные суммы.
CREATE TABLE salary_lines (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    period_id     BIGINT         NOT NULL,
    teacher_id    BIGINT         NOT NULL,
    lesson_id     BIGINT         NOT NULL,
    group_id      BIGINT         NOT NULL,
    lesson_date   DATE           NOT NULL,
    visits        INT            NOT NULL,
    unit_amount   DECIMAL(12, 2) NOT NULL,
    amount        DECIMAL(12, 2) NOT NULL,
    -- NULL означает, что для занятия не нашлось правила оплаты (сумма 0, период нельзя утвердить)
    rule_id       BIGINT         NULL,
    rule_snapshot LONGTEXT       NULL,
    calculated_at DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_salary_lines_lesson UNIQUE (period_id, lesson_id),
    CONSTRAINT fk_salary_lines_period FOREIGN KEY (period_id) REFERENCES salary_periods (id) ON DELETE RESTRICT,
    CONSTRAINT fk_salary_lines_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_salary_lines_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE RESTRICT,
    CONSTRAINT fk_salary_lines_group FOREIGN KEY (group_id) REFERENCES study_groups (id) ON DELETE RESTRICT,
    CONSTRAINT fk_salary_lines_rule FOREIGN KEY (rule_id) REFERENCES salary_rules (id) ON DELETE RESTRICT,
    CONSTRAINT ck_salary_lines_visits CHECK (visits > 0),
    CONSTRAINT ck_salary_lines_snapshot CHECK (rule_snapshot IS NULL OR JSON_VALID(rule_snapshot))
) ENGINE = InnoDB;

CREATE INDEX ix_salary_lines_period_teacher ON salary_lines (period_id, teacher_id);

-- Ручные премии (+) и удержания (-) с обязательной причиной
CREATE TABLE salary_adjustments (
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    period_id  BIGINT         NOT NULL,
    teacher_id BIGINT         NOT NULL,
    amount     DECIMAL(12, 2) NOT NULL,
    reason     VARCHAR(255)   NOT NULL,
    created_by VARCHAR(64)    NOT NULL,
    created_at DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_salary_adjustments_period FOREIGN KEY (period_id) REFERENCES salary_periods (id) ON DELETE RESTRICT,
    CONSTRAINT fk_salary_adjustments_teacher FOREIGN KEY (teacher_id) REFERENCES teachers (id) ON DELETE RESTRICT,
    CONSTRAINT ck_salary_adjustments_amount CHECK (amount <> 0)
) ENGINE = InnoDB;

CREATE INDEX ix_salary_adjustments_period_teacher ON salary_adjustments (period_id, teacher_id);
