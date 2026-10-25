package com.liketime.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Связь идентификатора человека на устройстве с учеником. */
@Entity
@Table(name = "device_persons")
public class DevicePerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "external_person_id", nullable = false, length = 100)
    private String externalPersonId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected DevicePerson() {
    }

    public DevicePerson(Long deviceId, String externalPersonId, Long studentId) {
        this.deviceId = deviceId;
        this.externalPersonId = externalPersonId;
        this.studentId = studentId;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        this.updatedAt = this.createdAt;
    }

    public void changeExternalId(String externalPersonId) {
        this.externalPersonId = externalPersonId;
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public String getExternalPersonId() {
        return externalPersonId;
    }

    public Long getStudentId() {
        return studentId;
    }
}
