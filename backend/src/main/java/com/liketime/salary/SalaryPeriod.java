package com.liketime.salary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "salary_periods")
public class SalaryPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PeriodStatus status;

    @Column(name = "calculated_at")
    private LocalDateTime calculatedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "paid_by", length = 64)
    private String paidBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SalaryPeriod() {
    }

    public SalaryPeriod(LocalDate startsOn, LocalDate endsOn) {
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.status = PeriodStatus.OPEN;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        this.updatedAt = this.createdAt;
    }

    public void markCalculated(LocalDateTime at) {
        this.status = PeriodStatus.CALCULATED;
        this.calculatedAt = at;
        touch();
    }

    public void approve(String by, LocalDateTime at) {
        this.status = PeriodStatus.APPROVED;
        this.approvedBy = by;
        this.approvedAt = at;
        touch();
    }

    public void markPaid(String by, LocalDateTime at) {
        this.status = PeriodStatus.PAID;
        this.paidBy = by;
        this.paidAt = at;
        touch();
    }

    /** Возврат из утверждённого состояния для исправлений; выплаченный период вернуть нельзя */
    public void reopen() {
        this.status = PeriodStatus.CALCULATED;
        this.approvedBy = null;
        this.approvedAt = null;
        touch();
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public PeriodStatus getStatus() {
        return status;
    }

    public LocalDateTime getCalculatedAt() {
        return calculatedAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public String getPaidBy() {
        return paidBy;
    }
}
