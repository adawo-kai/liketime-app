package com.liketime.salary;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalaryRuleRepository extends JpaRepository<SalaryRule, Long> {

    List<SalaryRule> findByActiveTrue();

    /** Все действующие правила с той же областью (для проверки пересечения по датам) */
    default List<SalaryRule> findActiveInScope(Long teacherId, Long groupId) {
        return findByActiveTrue().stream()
                .filter(r -> java.util.Objects.equals(r.getTeacherId(), teacherId)
                        && java.util.Objects.equals(r.getGroupId(), groupId))
                .toList();
    }

    default List<SalaryRule> findAllSorted() {
        return findAll(Sort.by(Sort.Order.desc("effectiveFrom"), Sort.Order.desc("id")));
    }
}
