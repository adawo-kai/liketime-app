package com.liketime.device;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DevicePersonRepository extends JpaRepository<DevicePerson, Long> {

    Optional<DevicePerson> findByDeviceIdAndExternalPersonId(Long deviceId, String externalPersonId);

    Optional<DevicePerson> findByDeviceIdAndStudentId(Long deviceId, Long studentId);

    List<DevicePerson> findByDeviceIdOrderByExternalPersonIdAsc(Long deviceId);
}
