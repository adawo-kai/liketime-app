package com.liketime.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Участие ученика в группе за период. Строки не удаляются: по ним считается состав группы на любую дату. */
@Entity
@Table(name = "group_students")
public class GroupMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "joined_on", nullable = false)
    private LocalDate joinedOn;

    @Column(name = "left_on")
    private LocalDate leftOn;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected GroupMembership() {
    }

    public GroupMembership(Long groupId, Long studentId, LocalDate joinedOn) {
        this.groupId = groupId;
        this.studentId = studentId;
        this.joinedOn = joinedOn;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        this.updatedAt = this.createdAt;
    }

    /** Закрывает участие. Дата выхода не может быть раньше даты вступления. */
    public void leave(LocalDate date) {
        this.leftOn = date.isBefore(joinedOn) ? joinedOn : date;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public LocalDate getJoinedOn() {
        return joinedOn;
    }

    public LocalDate getLeftOn() {
        return leftOn;
    }
}
