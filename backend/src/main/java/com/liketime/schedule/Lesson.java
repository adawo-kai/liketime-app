package com.liketime.schedule;

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

/** Конкретное занятие группы. Время хранится в UTC. */
@Entity
@Table(name = "lessons")
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "teacher_id")
    private Long teacherId;

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "slot_id")
    private Long slotId;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LessonStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LessonOrigin origin;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Lesson() {
    }

    public Lesson(Long groupId, Long teacherId, Long roomId, Long slotId, LocalDateTime startsAt,
                  LocalDateTime endsAt, LessonOrigin origin, String note) {
        this.groupId = groupId;
        this.teacherId = teacherId;
        this.roomId = roomId;
        this.slotId = slotId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.origin = origin;
        this.note = note;
        this.status = LessonStatus.PLANNED;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        this.updatedAt = this.createdAt;
    }

    public void reschedule(LocalDateTime startsAt, LocalDateTime endsAt) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        touch();
    }

    public void assign(Long teacherId, Long roomId) {
        this.teacherId = teacherId;
        this.roomId = roomId;
        touch();
    }

    public void setNote(String note) {
        this.note = note;
        touch();
    }

    public void setStatus(LessonStatus status) {
        this.status = status;
        touch();
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public Long getSlotId() {
        return slotId;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public LessonStatus getStatus() {
        return status;
    }

    public LessonOrigin getOrigin() {
        return origin;
    }

    public String getNote() {
        return note;
    }
}
