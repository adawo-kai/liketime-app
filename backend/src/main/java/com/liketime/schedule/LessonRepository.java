package com.liketime.schedule;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    @Query("select l from Lesson l where l.startsAt >= :from and l.startsAt < :to "
            + "and (:groupId is null or l.groupId = :groupId) "
            + "and (:teacherId is null or l.teacherId = :teacherId) "
            + "order by l.startsAt, l.id")
    List<Lesson> search(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                        @Param("groupId") Long groupId, @Param("teacherId") Long teacherId);

    boolean existsByGroupIdAndStartsAt(Long groupId, LocalDateTime startsAt);

    boolean existsByGroupIdAndStartsAtAndIdNot(Long groupId, LocalDateTime startsAt, Long id);

    /** Преподаватель уже занят в это время (отменённые занятия не считаются) */
    @Query("select count(l) > 0 from Lesson l where l.teacherId = :teacherId "
            + "and l.status <> com.liketime.schedule.LessonStatus.CANCELLED "
            + "and l.startsAt < :end and l.endsAt > :start and (:excludeId is null or l.id <> :excludeId)")
    boolean teacherBusy(@Param("teacherId") Long teacherId, @Param("start") LocalDateTime start,
                        @Param("end") LocalDateTime end, @Param("excludeId") Long excludeId);

    /** Кабинет уже занят в это время */
    @Query("select count(l) > 0 from Lesson l where l.roomId = :roomId "
            + "and l.status <> com.liketime.schedule.LessonStatus.CANCELLED "
            + "and l.startsAt < :end and l.endsAt > :start and (:excludeId is null or l.id <> :excludeId)")
    boolean roomBusy(@Param("roomId") Long roomId, @Param("start") LocalDateTime start,
                     @Param("end") LocalDateTime end, @Param("excludeId") Long excludeId);

    /**
     * Занятия, на которые может прийти ученик в момент времени: идут или начнутся в ближайшие минуты.
     * Используется при разборе событий устройства.
     */
    @Query("select l from Lesson l where l.groupId in :groupIds "
            + "and l.status <> com.liketime.schedule.LessonStatus.CANCELLED "
            + "and l.startsAt <= :opensBefore and l.endsAt >= :at order by l.startsAt")
    List<Lesson> findCandidates(@Param("groupIds") List<Long> groupIds, @Param("opensBefore") LocalDateTime opensBefore,
                                @Param("at") LocalDateTime at);
}
