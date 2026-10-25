package com.liketime.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_events")
public class DeviceEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "external_event_id", nullable = false, length = 100)
    private String externalEventId;

    @Column(name = "external_person_id", length = 100)
    private String externalPersonId;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 30)
    private EventMatchStatus matchStatus;

    @Column(name = "student_id")
    private Long studentId;

    @Column(name = "lesson_id")
    private Long lessonId;

    @Column(length = 255)
    private String note;

    @Column(name = "raw_payload", columnDefinition = "LONGTEXT")
    private String rawPayload;

    protected DeviceEvent() {
    }

    public DeviceEvent(Long deviceId, String externalEventId, String externalPersonId, LocalDateTime eventTime,
                       LocalDateTime receivedAt, BigDecimal confidence, String rawPayload) {
        this.deviceId = deviceId;
        this.externalEventId = externalEventId;
        this.externalPersonId = externalPersonId;
        this.eventTime = eventTime;
        this.receivedAt = receivedAt;
        this.confidence = confidence;
        this.rawPayload = rawPayload;
        this.matchStatus = EventMatchStatus.IGNORED;
    }

    public void applyResult(EventMatchStatus status, Long studentId, Long lessonId, String note) {
        this.matchStatus = status;
        this.studentId = studentId;
        this.lessonId = lessonId;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public String getExternalPersonId() {
        return externalPersonId;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public EventMatchStatus getMatchStatus() {
        return matchStatus;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public String getNote() {
        return note;
    }

    public String getRawPayload() {
        return rawPayload;
    }
}
