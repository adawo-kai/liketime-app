package com.liketime.student;

import com.liketime.common.Status;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Long> {

    List<Student> findByStatus(Status status, Sort sort);
}
