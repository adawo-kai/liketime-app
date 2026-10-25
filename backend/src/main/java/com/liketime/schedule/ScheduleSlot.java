package com.liketime.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;

/** Повторяющееся занятие группы: день недели и время по местным часам школы. */
@Entity
@Table(name = "schedule_slots")
public class ScheduleSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    /** ISO: 1 = понедельник ... 7 = воскресенье */
    @Column(nullable = false)
    private int weekday;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "room_id")
    private Long roomId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ScheduleSlot() {
    }

    public ScheduleSlot(Long groupId, int weekday, LocalTime startTime, LocalTime endTime, Long roomId) {
        this.groupId = groupId;
        this.weekday = weekday;
        this.startTime = startTime;
        this.endTime = endTime;
        this.roomId = roomId;
        this.active = true;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        this.updatedAt = this.createdAt;
    }

    public void update(int weekday, LocalTime startTime, LocalTime endTime, Long roomId) {
        this.weekday = weekday;
        this.startTime = startTime;
        this.endTime = endTime;
        this.roomId = roomId;
        touch();
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public int getWeekday() {
        return weekday;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public Long getRoomId() {
        return roomId;
    }

    public boolean isActive() {
        return active;
    }
}
