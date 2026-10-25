package com.liketime.group;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMembershipRepository extends JpaRepository<GroupMembership, Long> {

    List<GroupMembership> findByGroupIdOrderByJoinedOnAscIdAsc(Long groupId);

    List<GroupMembership> findByGroupIdAndLeftOnIsNull(Long groupId);

    List<GroupMembership> findByStudentIdAndLeftOnIsNull(Long studentId);

    Optional<GroupMembership> findByGroupIdAndStudentIdAndLeftOnIsNull(Long groupId, Long studentId);

    long countByGroupIdAndLeftOnIsNull(Long groupId);

    /** Состав группы на дату (день выхода ещё считается участием) */
    @Query("select m from GroupMembership m where m.groupId = :groupId and m.joinedOn <= :date "
            + "and (m.leftOn is null or m.leftOn >= :date)")
    List<GroupMembership> findByGroupIdOn(@Param("groupId") Long groupId, @Param("date") LocalDate date);

    /** В каких группах состоял ученик на дату */
    @Query("select m from GroupMembership m where m.studentId = :studentId and m.joinedOn <= :date "
            + "and (m.leftOn is null or m.leftOn >= :date)")
    List<GroupMembership> findByStudentIdOn(@Param("studentId") Long studentId, @Param("date") LocalDate date);
}
