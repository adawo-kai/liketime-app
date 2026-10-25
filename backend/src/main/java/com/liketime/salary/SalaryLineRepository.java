package com.liketime.salary;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalaryLineRepository extends JpaRepository<SalaryLine, Long> {

    List<SalaryLine> findByPeriodIdOrderByLessonDateAscIdAsc(Long periodId);

    List<SalaryLine> findByPeriodIdAndTeacherIdOrderByLessonDateAscIdAsc(Long periodId, Long teacherId);

    long countByPeriodIdAndRuleIdIsNull(Long periodId);

    // Без clearAutomatically: иначе загруженный в сессию период стал бы «отсоединённым» и его изменения потерялись бы
    @Modifying(flushAutomatically = true)
    @Query("delete from SalaryLine l where l.periodId = :periodId")
    void deleteByPeriod(@Param("periodId") Long periodId);

    /** По преподавателям: (teacherId, число занятий, число приходов, сумма) */
    @Query("select l.teacherId, count(l), sum(l.visits), sum(l.amount) from SalaryLine l "
            + "where l.periodId = :periodId group by l.teacherId")
    List<Object[]> totalsByTeacher(@Param("periodId") Long periodId);

    /** По периодам: (periodId, сумма начислений) */
    @Query("select l.periodId, sum(l.amount) from SalaryLine l group by l.periodId")
    List<Object[]> totalsByPeriod();
}
