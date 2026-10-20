package com.liketime.group;

import com.liketime.common.Status;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);

    boolean existsByTeacherIdAndStatus(Long teacherId, Status status);

    List<StudyGroup> findByTeacherId(Long teacherId, Sort sort);
}
