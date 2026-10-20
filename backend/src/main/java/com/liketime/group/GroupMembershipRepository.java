package com.liketime.group;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, Long> {

    List<GroupMembership> findByGroupIdOrderByJoinedOnAscIdAsc(Long groupId);

    List<GroupMembership> findByGroupIdAndLeftOnIsNull(Long groupId);

    List<GroupMembership> findByStudentIdAndLeftOnIsNull(Long studentId);

    Optional<GroupMembership> findByGroupIdAndStudentIdAndLeftOnIsNull(Long groupId, Long studentId);

    long countByGroupIdAndLeftOnIsNull(Long groupId);
}
