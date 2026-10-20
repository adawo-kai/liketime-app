package com.liketime.student;

import com.liketime.common.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(name = "parent_name", length = 150)
    private String parentName;

    @Column(name = "parent_phone", length = 20)
    private String parentPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Student() {
    }

    public Student(String fullName, String phone, String parentName, String parentPhone) {
        this.fullName = fullName;
        this.phone = phone;
        this.parentName = parentName;
        this.parentPhone = parentPhone;
        this.status = Status.ACTIVE;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        this.updatedAt = this.createdAt;
    }

    public void update(String fullName, String phone, String parentName, String parentPhone) {
        this.fullName = fullName;
        this.phone = phone;
        this.parentName = parentName;
        this.parentPhone = parentPhone;
        touch();
    }

    public void setStatus(Status status) {
        this.status = status;
        touch();
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getParentName() {
        return parentName;
    }

    public String getParentPhone() {
        return parentPhone;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
