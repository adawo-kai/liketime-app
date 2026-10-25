package com.liketime.salary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "salary_lines")
public class SalaryLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_id", nullable = false)
    private Long periodId;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "lesson_date", nullable = false)
    private LocalDate lessonDate;

    @Column(nullable = false)
    private int visits;

    @Column(name = "unit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitAmount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "rule_snapshot", columnDefinition = "LONGTEXT")
    private String ruleSnapshot;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    protected SalaryLine() {
    }

    public SalaryLine(Long periodId, Long teacherId, Long lessonId, Long groupId, LocalDate lessonDate, int visits,
                      BigDecimal unitAmount, Long ruleId, String ruleSnapshot, LocalDateTime calculatedAt) {
        this.periodId = periodId;
        this.teacherId = teacherId;
        this.lessonId = lessonId;
        this.groupId = groupId;
        this.lessonDate = lessonDate;
        this.visits = visits;
        this.unitAmount = unitAmount;
        this.amount = unitAmount.multiply(BigDecimal.valueOf(visits));
        this.ruleId = ruleId;
        this.ruleSnapshot = ruleSnapshot;
        this.calculatedAt = calculatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPeriodId() {
        return periodId;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public LocalDate getLessonDate() {
        return lessonDate;
    }

    public int getVisits() {
        return visits;
    }

    public BigDecimal getUnitAmount() {
        return unitAmount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public String getRuleSnapshot() {
        return ruleSnapshot;
    }
}
