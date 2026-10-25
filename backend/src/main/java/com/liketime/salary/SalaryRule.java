package com.liketime.salary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "salary_rules")
public class SalaryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(name = "group_id")
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 20)
    private RuleType ruleType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 255)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SalaryRule() {
    }

    public SalaryRule(Long teacherId, Long groupId, BigDecimal amount, LocalDate effectiveFrom,
                      LocalDate effectiveTo, String comment) {
        this.teacherId = teacherId;
        this.groupId = groupId;
        this.ruleType = RuleType.PER_VISIT;
        this.amount = amount;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.comment = comment;
        this.active = true;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        this.updatedAt = this.createdAt;
    }

    /** Область действия (преподаватель, группа) после создания не меняется */
    public void update(BigDecimal amount, LocalDate effectiveFrom, LocalDate effectiveTo, String comment) {
        this.amount = amount;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.comment = comment;
        touch();
    }

    public void setActive(boolean active) {
        this.active = active;
        touch();
    }

    /** Правило действует в этот день и подходит для преподавателя и группы */
    public boolean appliesTo(Long teacher, Long group, LocalDate date) {
        return active
                && (teacherId == null || teacherId.equals(teacher))
                && (groupId == null || groupId.equals(group))
                && !date.isBefore(effectiveFrom)
                && (effectiveTo == null || !date.isAfter(effectiveTo));
    }

    /** Чем конкретнее правило, тем выше приоритет: группа важнее преподавателя, оба вместе важнее всего */
    public int specificity() {
        return (groupId != null ? 2 : 0) + (teacherId != null ? 1 : 0);
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public boolean isActive() {
        return active;
    }

    public String getComment() {
        return comment;
    }
}
