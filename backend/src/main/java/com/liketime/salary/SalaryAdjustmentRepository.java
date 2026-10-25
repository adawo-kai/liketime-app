package com.liketime.salary;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalaryAdjustmentRepository extends JpaRepository<SalaryAdjustment, Long> {

    List<SalaryAdjustment> findByPeriodIdOrderByIdAsc(Long periodId);

    List<SalaryAdjustment> findByPeriodIdAndTeacherIdOrderByIdAsc(Long periodId, Long teacherId);

    boolean existsByPeriodId(Long periodId);

    /** По преподавателям: (teacherId, сумма корректировок) */
    @Query("select a.teacherId, sum(a.amount) from SalaryAdjustment a where a.periodId = :periodId group by a.teacherId")
    List<Object[]> totalsByTeacher(@Param("periodId") Long periodId);

    /** По периодам: (periodId, сумма корректировок) */
    @Query("select a.periodId, sum(a.amount) from SalaryAdjustment a group by a.periodId")
    List<Object[]> totalsByPeriod();
}
