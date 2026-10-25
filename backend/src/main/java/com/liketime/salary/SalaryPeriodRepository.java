package com.liketime.salary;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalaryPeriodRepository extends JpaRepository<SalaryPeriod, Long> {

    List<SalaryPeriod> findAllByOrderByStartsOnDesc();

    /** Есть ли период, который пересекается с указанными датами */
    @Query("select count(p) > 0 from SalaryPeriod p where p.startsOn <= :to and p.endsOn >= :from")
    boolean existsOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Дата попадает в утверждённый или выплаченный период */
    @Query("select count(p) > 0 from SalaryPeriod p where p.status in "
            + "(com.liketime.salary.PeriodStatus.APPROVED, com.liketime.salary.PeriodStatus.PAID) "
            + "and p.startsOn <= :date and p.endsOn >= :date")
    boolean existsLockedOn(@Param("date") LocalDate date);
}
