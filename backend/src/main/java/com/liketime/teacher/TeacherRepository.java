package com.liketime.teacher;

import com.liketime.common.Status;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    List<Teacher> findByStatus(Status status, Sort sort);

    Optional<Teacher> findByUserId(Long userId);
}
