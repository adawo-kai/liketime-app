package com.liketime.device;

import com.liketime.audit.AuditService;
import com.liketime.common.Status;
import com.liketime.device.DeviceController.DeviceCreateRequest;
import com.liketime.device.DeviceController.DeviceUpdateRequest;
import com.liketime.student.Student;
import com.liketime.student.StudentRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Список устройств и привязка лиц устройства к ученикам. */
@Service
@Transactional
public class DeviceAdminService {

    private final DeviceRepository devices;
    private final DevicePersonRepository persons;
    private final DeviceEventRepository events;
    private final StudentRepository students;
    private final DeviceProperties properties;
    private final AuditService audit;

    public DeviceAdminService(DeviceRepository devices, DevicePersonRepository persons, DeviceEventRepository events,
                              StudentRepository students, DeviceProperties properties, AuditService audit) {
        this.devices = devices;
        this.persons = persons;
        this.events = events;
        this.students = students;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<DeviceView> list() {
        return devices.findAll(Sort.by("name")).stream().map(this::view).toList();
    }

    public DeviceView create(DeviceCreateRequest r, Authentication auth) {
        String serial = r.serialNumber().trim();
        if (devices.existsBySerialNumber(serial)) {
            throw conflict("Устройство с таким серийным номером уже есть");
        }
        Device saved = devices.save(new Device(r.name().trim(), blank(r.model()), serial, blank(r.location()),
                blank(r.host()), r.secretRef().trim()));
        audit.record(auth, "DEVICE_CREATE", "device", saved.getId(), describe(saved));
        return view(saved);
    }

    public DeviceView update(Long id, DeviceUpdateRequest r, Authentication auth) {
        Device device = find(id);
        device.update(r.name().trim(), blank(r.model()), blank(r.location()), blank(r.host()), r.secretRef().trim());
        audit.record(auth, "DEVICE_UPDATE", "device", id, describe(device));
        return view(device);
    }

    public DeviceView setStatus(Long id, DeviceStatus status, Authentication auth) {
        Device device = find(id);
        device.setStatus(status);
        audit.record(auth, "DEVICE_STATUS", "device", id, Map.of("status", status.name()));
        return view(device);
    }

    @Transactional(readOnly = true)
    public List<PersonView> persons(Long deviceId) {
        find(deviceId);
        List<DevicePerson> found = persons.findByDeviceIdOrderByExternalPersonIdAsc(deviceId);
        Map<Long, String> names = students.findAllById(found.stream().map(DevicePerson::getStudentId).toList())
                .stream().collect(Collectors.toMap(Student::getId, Student::getFullName));
        return found.stream()
                .map(p -> new PersonView(p.getExternalPersonId(), p.getStudentId(), names.get(p.getStudentId())))
                .toList();
    }

    /**
     * Привязывает лицо устройства к ученику. Если у ученика уже была привязка на этом устройстве
     * (лицо перерегистрировали), идентификатор обновляется.
     */
    public PersonView link(Long deviceId, String externalPersonId, Long studentId, Authentication auth) {
        find(deviceId);
        String external = externalPersonId.trim();
        Student student = students.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ученик не найден"));
        if (student.getStatus() != Status.ACTIVE) {
            throw conflict("Нельзя привязать неактивного ученика");
        }
        DevicePerson sameExternal = persons.findByDeviceIdAndExternalPersonId(deviceId, external).orElse(null);
        if (sameExternal != null && !sameExternal.getStudentId().equals(studentId)) {
            throw conflict("Этот идентификатор уже привязан к другому ученику");
        }
        DevicePerson byStudent = persons.findByDeviceIdAndStudentId(deviceId, studentId).orElse(null);
        if (byStudent != null) {
            byStudent.changeExternalId(external);
        } else if (sameExternal == null) {
            persons.save(new DevicePerson(deviceId, external, studentId));
        }
        audit.record(auth, "DEVICE_LINK", "device", deviceId, Map.of("personId", external, "studentId", studentId));
        return new PersonView(external, studentId, student.getFullName());
    }

    public void unlink(Long deviceId, String externalPersonId, Authentication auth) {
        find(deviceId);
        DevicePerson person = persons.findByDeviceIdAndExternalPersonId(deviceId, externalPersonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Привязки нет"));
        audit.record(auth, "DEVICE_UNLINK", "device", deviceId,
                Map.of("personId", externalPersonId, "studentId", person.getStudentId()));
        persons.delete(person);
    }

    /** Лица, которые устройство распознало, но которые ещё не привязаны к ученикам */
    @Transactional(readOnly = true)
    public List<UnmatchedView> unmatched(Long deviceId) {
        find(deviceId);
        return events.unmatchedPersons(deviceId).stream()
                .map(row -> new UnmatchedView((String) row[0], (Long) row[1],
                        ((java.time.LocalDateTime) row[2]).toInstant(ZoneOffset.UTC)))
                .toList();
    }

    private Device find(Long id) {
        return devices.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Устройство не найдено"));
    }

    private DeviceView view(Device d) {
        boolean secretConfigured = d.getSecretRef() != null && properties.secrets().containsKey(d.getSecretRef());
        return new DeviceView(d.getId(), d.getName(), d.getModel(), d.getSerialNumber(), d.getLocation(), d.getHost(),
                d.getStatus(), d.getLastSeenAt() != null ? d.getLastSeenAt().toInstant(ZoneOffset.UTC) : null,
                d.getSecretRef(), secretConfigured);
    }

    private static Map<String, Object> describe(Device d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", d.getName());
        m.put("serialNumber", d.getSerialNumber());
        m.put("secretRef", d.getSecretRef());
        return m;
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    /** secretConfigured = false означает, что секрет с таким именем не задан в конфигурации сервера */
    public record DeviceView(Long id, String name, String model, String serialNumber, String location, String host,
                             DeviceStatus status, Instant lastSeenAt, String secretRef, boolean secretConfigured) {
    }

    public record PersonView(String externalPersonId, Long studentId, String studentName) {
    }

    public record UnmatchedView(String externalPersonId, long events, Instant lastSeenAt) {
    }
}
