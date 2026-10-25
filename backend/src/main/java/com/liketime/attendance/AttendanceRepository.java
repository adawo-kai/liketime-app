package com.liketime.attendance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    List<Attendance> findByLessonId(Long lessonId);

    List<Attendance> findByLessonIdIn(Collection<Long> lessonIds);

    Optional<Attendance> findByLessonIdAndStudentId(Long lessonId, Long studentId);

    boolean existsByLessonIdAndStatusIn(Long lessonId, Collection<AttendanceStatus> statuses);

    /** Число приходов по занятиям: пары (lessonId, count) */
    @Query("select a.lessonId, count(a) from Attendance a where a.lessonId in :lessonIds "
            + "and a.status in :statuses group by a.lessonId")
    List<Object[]> countByLesson(@Param("lessonIds") Collection<Long> lessonIds,
                                 @Param("statuses") Collection<AttendanceStatus> statuses);
}
