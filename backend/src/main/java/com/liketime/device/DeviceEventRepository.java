package com.liketime.device;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceEventRepository extends JpaRepository<DeviceEvent, Long> {

    Optional<DeviceEvent> findByDeviceIdAndExternalEventId(Long deviceId, String externalEventId);

    @Query("select e from DeviceEvent e where e.eventTime >= :from and e.eventTime < :to "
            + "and (:status is null or e.matchStatus = :status) order by e.eventTime desc, e.id desc")
    List<DeviceEvent> search(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                             @Param("status") EventMatchStatus status, Pageable pageable);

    @Query("select e from DeviceEvent e where e.matchStatus in :statuses "
            + "and e.eventTime >= :from and e.eventTime < :to order by e.eventTime, e.id")
    List<DeviceEvent> findForReprocess(@Param("statuses") Collection<EventMatchStatus> statuses,
                                       @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Незнакомые лица устройства: (идентификатор, число событий, время последнего события) */
    @Query("select e.externalPersonId, count(e), max(e.eventTime) from DeviceEvent e "
            + "where e.deviceId = :deviceId and e.matchStatus = com.liketime.device.EventMatchStatus.UNKNOWN_PERSON "
            + "and e.externalPersonId is not null group by e.externalPersonId order by max(e.eventTime) desc")
    List<Object[]> unmatchedPersons(@Param("deviceId") Long deviceId);
}
