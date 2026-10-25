package com.liketime.attendance;

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
@Table(name = "attendance")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttendanceSource source;

    @Column(name = "check_in_at")
    private LocalDateTime checkInAt;

    @Column(name = "recorded_by", length = 64)
    private String recordedBy;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Attendance() {
    }

    public Attendance(Long lessonId, Long studentId) {
        this.lessonId = lessonId;
        this.studentId = studentId;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        this.updatedAt = this.createdAt;
    }

    /** Ручная отметка. Время прихода по устройству сохраняется, чтобы история не терялась. */
    public void markManually(AttendanceStatus status, String note, String login) {
        this.status = status;
        this.source = AttendanceSource.MANUAL;
        this.note = note;
        this.recordedBy = login;
        touch();
    }

    /** Отметка устройства: приход и статус считаются по времени события */
    public void markByDevice(AttendanceStatus status, LocalDateTime checkInAt) {
        this.status = status;
        this.source = AttendanceSource.DEVICE;
        this.checkInAt = checkInAt;
        this.recordedBy = "device";
        touch();
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public AttendanceSource getSource() {
        return source;
    }

    public LocalDateTime getCheckInAt() {
        return checkInAt;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public String getNote() {
        return note;
    }
}
