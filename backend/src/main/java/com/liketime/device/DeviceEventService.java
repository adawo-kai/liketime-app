package com.liketime.device;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liketime.attendance.AttendanceProperties;
import com.liketime.attendance.AttendanceService;
import com.liketime.attendance.AttendanceService.DeviceOutcome;
import com.liketime.common.AppTime;
import com.liketime.common.Status;
import com.liketime.group.GroupMembership;
import com.liketime.group.GroupMembershipRepository;
import com.liketime.schedule.Lesson;
import com.liketime.schedule.LessonRepository;
import com.liketime.student.Student;
import com.liketime.student.StudentRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Приём событий устройств: проверка ключа, защита от дублей, сопоставление с учеником и занятием
 * и отметка посещаемости.
 */
@Service
@Transactional
public class DeviceEventService {

    private static final int MAX_LIST = 500;

    private final DeviceRepository devices;
    private final DeviceEventRepository events;
    private final DevicePersonRepository persons;
    private final StudentRepository students;
    private final GroupMembershipRepository memberships;
    private final LessonRepository lessons;
    private final AttendanceService attendance;
    private final AttendanceProperties attendanceProperties;
    private final DeviceProperties properties;
    private final List<DeviceEventParser> parsers;
    private final ObjectMapper objectMapper;
    private final AppTime time;

    public DeviceEventService(DeviceRepository devices, DeviceEventRepository events, DevicePersonRepository persons,
                              StudentRepository students, GroupMembershipRepository memberships,
                              LessonRepository lessons, AttendanceService attendance,
                              AttendanceProperties attendanceProperties, DeviceProperties properties,
                              List<DeviceEventParser> parsers, ObjectMapper objectMapper, AppTime time) {
        this.devices = devices;
        this.events = events;
        this.persons = persons;
        this.students = students;
        this.memberships = memberships;
        this.lessons = lessons;
        this.attendance = attendance;
        this.attendanceProperties = attendanceProperties;
        this.properties = properties;
        this.parsers = parsers;
        this.objectMapper = objectMapper;
        this.time = time;
    }

    /** Принимает одно событие устройства. Повторная отправка того же события безопасна. */
    public IngestResult ingest(String serial, String key, String body) {
        Device device = authenticate(serial, key);
        JsonNode payload = readJson(body);
        NormalizedEvent event = parserFor(device).parse(payload);
        LocalDateTime now = time.nowUtc();
        if (event.timeUtc().isAfter(now.plusMinutes(properties.futureToleranceMinutes()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Время события в будущем. Проверьте часы устройства");
        }
        device.seen(now);

        Optional<DeviceEvent> duplicate = events.findByDeviceIdAndExternalEventId(device.getId(), event.eventId());
        if (duplicate.isPresent()) {
            return IngestResult.of(duplicate.get(), true);
        }
        DeviceEvent saved = events.save(new DeviceEvent(device.getId(), event.eventId(), event.personId(),
                event.timeUtc(), now, event.confidence(), body));
        Match match = match(device.getId(), event.personId(), event.confidence(), event.timeUtc());
        saved.applyResult(match.status(), match.studentId(), match.lessonId(), match.note());
        return IngestResult.of(saved, false);
    }

    /** Сообщает, что устройство на связи, даже если никто не приходил */
    public void heartbeat(String serial, String key) {
        authenticate(serial, key).seen(time.nowUtc());
    }

    /**
     * Повторно разбирает сохранённые события, например после привязки лица к ученику или создания
     * недостающего занятия. События обрабатываются в порядке времени.
     */
    public ReprocessResult reprocess(LocalDate from, LocalDate to, Collection<EventMatchStatus> statuses) {
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата окончания раньше даты начала");
        }
        if (Duration.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()).toDays() > 93) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Период не должен быть больше 93 дней");
        }
        List<DeviceEvent> found = events.findForReprocess(statuses, time.startOfDayUtc(from),
                time.startOfDayUtc(to.plusDays(1)));
        Map<EventMatchStatus, Integer> result = new EnumMap<>(EventMatchStatus.class);
        for (DeviceEvent e : found) {
            Match match = match(e.getDeviceId(), e.getExternalPersonId(), e.getConfidence(), e.getEventTime());
            e.applyResult(match.status(), match.studentId(), match.lessonId(), match.note());
            result.merge(match.status(), 1, Integer::sum);
        }
        return new ReprocessResult(found.size(), result);
    }

    @Transactional(readOnly = true)
    public List<EventView> list(LocalDate from, LocalDate to, EventMatchStatus status, int limit) {
        int size = Math.max(1, Math.min(limit, MAX_LIST));
        List<DeviceEvent> found = events.search(time.startOfDayUtc(from), time.startOfDayUtc(to.plusDays(1)),
                status, PageRequest.of(0, size));
        Map<Long, String> deviceNames = devices.findAllById(
                        found.stream().map(DeviceEvent::getDeviceId).distinct().toList()).stream()
                .collect(Collectors.toMap(Device::getId, Device::getName));
        Map<Long, String> studentNames = students.findAllById(
                        found.stream().map(DeviceEvent::getStudentId).filter(java.util.Objects::nonNull)
                                .distinct().toList()).stream()
                .collect(Collectors.toMap(Student::getId, Student::getFullName));
        return found.stream().map(e -> new EventView(e.getId(), e.getDeviceId(), deviceNames.get(e.getDeviceId()),
                e.getExternalEventId(), e.getExternalPersonId(), e.getEventTime().toInstant(ZoneOffset.UTC),
                e.getConfidence(), e.getMatchStatus(), e.getStudentId(),
                e.getStudentId() != null ? studentNames.get(e.getStudentId()) : null,
                e.getLessonId(), e.getNote())).toList();
    }

    /** Подбирает ученика и занятие для события и ставит отметку */
    private Match match(Long deviceId, String personId, BigDecimal confidence, LocalDateTime at) {
        if (confidence != null && confidence.doubleValue() < properties.minConfidence()) {
            return new Match(EventMatchStatus.LOW_CONFIDENCE, null, null, "Уверенность распознавания ниже порога");
        }
        DevicePerson person = personId == null ? null
                : persons.findByDeviceIdAndExternalPersonId(deviceId, personId).orElse(null);
        if (person == null) {
            return new Match(EventMatchStatus.UNKNOWN_PERSON, null, null, "Лицо не привязано к ученику");
        }
        Student student = students.findById(person.getStudentId()).orElseThrow();
        if (student.getStatus() != Status.ACTIVE) {
            return new Match(EventMatchStatus.IGNORED, student.getId(), null, "Ученик неактивен");
        }
        LocalDate date = time.localDate(at);
        List<Long> groupIds = memberships.findByStudentIdOn(student.getId(), date).stream()
                .map(GroupMembership::getGroupId).distinct().toList();
        if (groupIds.isEmpty()) {
            return new Match(EventMatchStatus.NO_LESSON, student.getId(), null, "Ученик не состоит ни в одной группе");
        }
        List<Lesson> candidates = lessons.findCandidates(groupIds,
                at.plusMinutes(attendanceProperties.earlyMinutes()), at);
        if (candidates.isEmpty()) {
            return new Match(EventMatchStatus.NO_LESSON, student.getId(), null, "В это время у ученика нет занятия");
        }
        Lesson lesson = candidates.stream()
                .min(Comparator.comparingLong((Lesson l) -> Math.abs(Duration.between(l.getStartsAt(), at).toSeconds())))
                .orElseThrow();
        DeviceOutcome outcome = attendance.applyDeviceCheckIn(lesson, student.getId(), at);
        return switch (outcome) {
            case LOCKED -> new Match(EventMatchStatus.IGNORED, student.getId(), lesson.getId(),
                    "Зарплатный период закрыт, отметка не изменена");
            case KEPT_MANUAL -> new Match(EventMatchStatus.MATCHED, student.getId(), lesson.getId(),
                    "Есть ручная отметка, она сохранена");
            case RECORDED -> new Match(EventMatchStatus.MATCHED, student.getId(), lesson.getId(), "Приход отмечен");
            case UPDATED -> new Match(EventMatchStatus.MATCHED, student.getId(), lesson.getId(),
                    "Отметка уже была, время прихода сохранено");
        };
    }

    /**
     * Проверяет устройство и ключ. На неизвестное устройство и неверный ключ отвечаем одинаково,
     * чтобы нельзя было перебором узнать серийные номера.
     */
    private Device authenticate(String serial, String key) {
        Device device = devices.findBySerialNumber(serial).orElse(null);
        String expected = device != null && device.getSecretRef() != null
                ? properties.secrets().get(device.getSecretRef()) : null;
        byte[] actualBytes = (key == null ? "" : key).getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = (expected == null ? "" : expected).getBytes(StandardCharsets.UTF_8);
        boolean keyOk = MessageDigest.isEqual(actualBytes, expectedBytes) && expected != null && !expected.isEmpty();
        if (device == null || !keyOk) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неизвестное устройство или неверный ключ");
        }
        if (device.getStatus() != DeviceStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Устройство отключено");
        }
        return device;
    }

    private DeviceEventParser parserFor(Device device) {
        return parsers.stream().filter(p -> p.supports(device)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Для этого устройства нет разборщика событий"));
    }

    private JsonNode readJson(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node == null || !node.isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ожидается JSON-объект");
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный JSON");
        }
    }

    private record Match(EventMatchStatus status, Long studentId, Long lessonId, String note) {
    }

    public record IngestResult(Long id, EventMatchStatus status, Long studentId, Long lessonId, String note,
                               boolean duplicate) {
        static IngestResult of(DeviceEvent e, boolean duplicate) {
            return new IngestResult(e.getId(), e.getMatchStatus(), e.getStudentId(), e.getLessonId(), e.getNote(),
                    duplicate);
        }
    }

    public record ReprocessResult(int processed, Map<EventMatchStatus, Integer> byStatus) {
    }

    public record EventView(Long id, Long deviceId, String deviceName, String externalEventId,
                            String externalPersonId, Instant eventTime, BigDecimal confidence,
                            EventMatchStatus status, Long studentId, String studentName, Long lessonId,
                            String note) {
    }
}
