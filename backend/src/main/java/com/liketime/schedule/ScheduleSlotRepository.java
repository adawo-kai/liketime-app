package com.liketime.schedule;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleSlotRepository extends JpaRepository<ScheduleSlot, Long> {

    List<ScheduleSlot> findByGroupIdOrderByWeekdayAscStartTimeAsc(Long groupId);

    List<ScheduleSlot> findByGroupIdAndActiveTrue(Long groupId);

    boolean existsByGroupIdAndWeekdayAndStartTimeAndIdNot(Long groupId, int weekday, java.time.LocalTime startTime,
                                                          Long id);

    boolean existsByGroupIdAndWeekdayAndStartTime(Long groupId, int weekday, java.time.LocalTime startTime);
}
