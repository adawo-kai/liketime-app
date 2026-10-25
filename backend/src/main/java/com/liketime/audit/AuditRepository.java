package com.liketime.audit;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditRepository extends JpaRepository<AuditEntry, Long> {

    @Query("select a from AuditEntry a where (:entity is null or a.entity = :entity) "
            + "and (:entityId is null or a.entityId = :entityId) order by a.id desc")
    List<AuditEntry> search(@Param("entity") String entity, @Param("entityId") Long entityId, Pageable pageable);
}
