package com.liketime.device;

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
@Table(name = "devices")
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String model;

    @Column(name = "serial_number", nullable = false, unique = true, length = 100)
    private String serialNumber;

    @Column(length = 150)
    private String location;

    @Column(length = 255)
    private String host;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceStatus status;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    /** Имя секрета в конфигурации (liketime.devices.secrets.&lt;ref&gt;), а не сам секрет */
    @Column(name = "secret_ref", length = 100)
    private String secretRef;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Device() {
    }

    public Device(String name, String model, String serialNumber, String location, String host, String secretRef) {
        this.name = name;
        this.model = model;
        this.serialNumber = serialNumber;
        this.location = location;
        this.host = host;
        this.secretRef = secretRef;
        this.status = DeviceStatus.ACTIVE;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        this.updatedAt = this.createdAt;
    }

    public void update(String name, String model, String location, String host, String secretRef) {
        this.name = name;
        this.model = model;
        this.location = location;
        this.host = host;
        this.secretRef = secretRef;
        touch();
    }

    public void setStatus(DeviceStatus status) {
        this.status = status;
        touch();
    }

    public void seen(LocalDateTime at) {
        this.lastSeenAt = at;
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getModel() {
        return model;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getLocation() {
        return location;
    }

    public String getHost() {
        return host;
    }

    public DeviceStatus getStatus() {
        return status;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public String getSecretRef() {
        return secretRef;
    }
}
