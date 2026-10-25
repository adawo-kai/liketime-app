package com.liketime.schedule;

import com.liketime.attendance.AttendanceRepository;
import com.liketime.attendance.AttendanceStatus;
import com.liketime.audit.AuditService;
import com.liketime.common.AccessScope;
import com.liketime.common.AppTime;
import com.liketime.common.PeriodLock;
import com.liketime.common.Status;
import com.liketime.group.StudyGroup;
import com.liketime.group.StudyGroupRepository;
import com.liketime.room.Room;
import com.liketime.room.RoomRepository;
import com.liketime.schedule.LessonController.LessonCreateRequest;
import com.liketime.schedule.LessonController.LessonUpdateRequest;
import com.liketime.teacher.Teacher;
import com.liketime.teacher.TeacherRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class LessonService {

    private static final int MAX_RANGE_DAYS = 100;

    private final LessonRepository lessons;
    private final StudyGroupRepository groups;
    private final TeacherRepository teachers;
    private final RoomRepository rooms;
    private final AttendanceRepository attendance;
    private final AccessScope access;
    private final AppTime time;
    private final AuditService audit;
    private final PeriodLock periodLock;

    public LessonService(LessonRepository lessons, StudyGroupRepository groups, TeacherRepository teachers,
                         RoomRepository rooms, AttendanceRepository attendance, AccessScope access,
                         AppTime time, AuditService audit, PeriodLock periodLock) {
        this.lessons = lessons;
        this.groups = groups;
        this.teachers = teachers;
        this.rooms = rooms;
        this.attendance = attendance;
        this.access = access;
        this.time = time;
        this.audit = audit;
        this.periodLock = periodLock;
    }

    /** Занятия за период (даты включительно, по местному времени). Преподаватель видит только свои. */
    @Transactional(readOnly = true)
    public List<LessonView> list(LocalDate from, LocalDate to, Long groupId, Long teacherId, Authentication auth) {
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата окончания раньше даты начала");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Период не должен быть больше " + MAX_RANGE_DAYS + " дней");
        }
        Long effectiveTeacher = teacherId;
        if (!access.isStaff(auth)) {
            Long own = access.currentTeacherId(auth).orElse(null);
            if (own == null) {
                return List.of();
            }
            effectiveTeacher = own;
        }
        return views(lessons.search(time.startOfDayUtc(from), time.startOfDayUtc(to.plusDays(1)),
                groupId, effectiveTeacher));
    }

    @Transactional(readOnly = true)
    public LessonView get(Long id, Authentication auth) {
        return views(List.of(findAccessible(id, auth))).get(0);
    }

    public LessonView create(LessonCreateRequest r, Authentication auth) {
        StudyGroup group = groups.findById(r.groupId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Группа не найдена"));
        if (group.getStatus() != Status.ACTIVE) {
            throw conflict("Нельзя создавать занятия архивной группы");
        }
        Long teacherId = r.teacherId() != null ? r.teacherId() : group.getTeacherId();
        validate(r.date(), r.startTime(), r.endTime(), teacherId, r.roomId());
        LocalDateTime startsAt = time.toUtc(r.date(), r.startTime());
        LocalDateTime endsAt = time.toUtc(r.date(), r.endTime());
        periodLock.assertEditable(r.date());
        if (lessons.existsByGroupIdAndStartsAt(group.getId(), startsAt)) {
            throw conflict("У группы уже есть занятие в это время");
        }
        checkBusy(teacherId, r.roomId(), startsAt, endsAt, null);

        Lesson saved = lessons.save(new Lesson(group.getId(), teacherId, r.roomId(), null, startsAt, endsAt,
                LessonOrigin.MANUAL, blankToNull(r.note())));
        audit.record(auth, "LESSON_CREATE", "lesson", saved.getId(), describe(saved));
        return views(List.of(saved)).get(0);
    }

    /** Перенос времени допускается только для занятия без отметок; преподавателя, кабинет и заметку можно менять и после. */
    public LessonView update(Long id, LessonUpdateRequest r, Authentication auth) {
        Lesson lesson = find(id);
        if (lesson.getStatus() == LessonStatus.CANCELLED) {
            throw conflict("Отменённое занятие изменить нельзя");
        }
        validate(r.date(), r.startTime(), r.endTime(), r.teacherId(), r.roomId());
        LocalDateTime startsAt = time.toUtc(r.date(), r.startTime());
        LocalDateTime endsAt = time.toUtc(r.date(), r.endTime());
        boolean timeChanged = !startsAt.equals(lesson.getStartsAt()) || !endsAt.equals(lesson.getEndsAt());
        periodLock.assertEditable(time.localDate(lesson.getStartsAt()));
        if (timeChanged) {
            periodLock.assertEditable(r.date());
            if (lesson.getStatus() != LessonStatus.PLANNED
                    || !attendance.findByLessonId(id).isEmpty()) {
                throw conflict("Нельзя переносить занятие, по которому уже есть отметки");
            }
            if (lessons.existsByGroupIdAndStartsAtAndIdNot(lesson.getGroupId(), startsAt, id)) {
                throw conflict("У группы уже есть занятие в это время");
            }
        }
        checkBusy(r.teacherId(), r.roomId(), startsAt, endsAt, id);

        Map<String, Object> before = describe(lesson);
        if (timeChanged) {
            lesson.reschedule(startsAt, endsAt);
        }
        lesson.assign(r.teacherId(), r.roomId());
        lesson.setNote(blankToNull(r.note()));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("before", before);
        details.put("after", describe(lesson));
        audit.record(auth, "LESSON_UPDATE", "lesson", id, details);
        return views(List.of(lesson)).get(0);
    }

    /**
     * Смена статуса. Преподаватель может только отметить своё занятие проведённым, остальное делает персонал.
     * Занятие нельзя отметить проведённым до начала и без назначенного преподавателя.
     */
    public LessonView changeStatus(Long id, LessonStatus target, Authentication auth) {
        Lesson lesson = findAccessible(id, auth);
        if (lesson.getStatus() == target) {
            return views(List.of(lesson)).get(0);
        }
        boolean staff = access.isStaff(auth);
        if (!staff && (target != LessonStatus.HELD || lesson.getStatus() != LessonStatus.PLANNED)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Преподаватель может только отметить своё запланированное занятие проведённым");
        }
        periodLock.assertEditable(time.localDate(lesson.getStartsAt()));

        switch (target) {
            case HELD -> {
                if (lesson.getStatus() == LessonStatus.CANCELLED) {
                    throw conflict("Отменённое занятие сначала верните в план");
                }
                if (lesson.getTeacherId() == null) {
                    throw conflict("Назначьте занятию преподавателя");
                }
                if (time.nowUtc().isBefore(lesson.getStartsAt())) {
                    throw conflict("Занятие ещё не началось");
                }
            }
            case CANCELLED, PLANNED -> {
                if (attendance.existsByLessonIdAndStatusIn(id, AttendanceStatus.VISITS)) {
                    throw conflict("По занятию есть отметки о приходе. Сначала снимите их");
                }
            }
            default -> { }
        }
        LessonStatus before = lesson.getStatus();
        lesson.setStatus(target);
        audit.record(auth, "LESSON_STATUS", "lesson", id, Map.of("before", before.name(), "after", target.name()));
        return views(List.of(lesson)).get(0);
    }

    private void validate(LocalDate date, LocalTime start, LocalTime end, Long teacherId, Long roomId) {
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Время окончания должно быть позже начала");
        }
        if (teacherId != null) {
            Teacher teacher = teachers.findById(teacherId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Преподаватель не найден"));
            if (teacher.getStatus() != Status.ACTIVE) {
                throw conflict("Нельзя назначить неактивного преподавателя");
            }
        }
        if (roomId != null && !rooms.existsById(roomId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Кабинет не найден");
        }
    }

    private void checkBusy(Long teacherId, Long roomId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        if (teacherId != null && lessons.teacherBusy(teacherId, start, end, excludeId)) {
            throw conflict("Преподаватель в это время ведёт другое занятие");
        }
        if (roomId != null && lessons.roomBusy(roomId, start, end, excludeId)) {
            throw conflict("Кабинет в это время занят");
        }
    }

    private Lesson find(Long id) {
        return lessons.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Занятие не найдено"));
    }

    private Lesson findAccessible(Long id, Authentication auth) {
        Lesson lesson = find(id);
        if (access.isStaff(auth)) {
            return lesson;
        }
        boolean own = access.currentTeacherId(auth).filter(t -> t.equals(lesson.getTeacherId())).isPresent();
        if (!own) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Занятие не найдено");
        }
        return lesson;
    }

    private Map<String, Object> describe(Lesson l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("groupId", l.getGroupId());
        m.put("teacherId", l.getTeacherId());
        m.put("roomId", l.getRoomId());
        m.put("date", time.localDate(l.getStartsAt()).toString());
        m.put("start", time.localTime(l.getStartsAt()).toString());
        m.put("end", time.localTime(l.getEndsAt()).toString());
        m.put("status", l.getStatus().name());
        return m;
    }

    private List<LessonView> views(List<Lesson> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, String> groupNames = groups.findAllById(
                        list.stream().map(Lesson::getGroupId).distinct().toList()).stream()
                .collect(Collectors.toMap(StudyGroup::getId, StudyGroup::getName));
        Map<Long, String> teacherNames = teachers.findAllById(
                        list.stream().map(Lesson::getTeacherId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Teacher::getId, Teacher::getFullName));
        Map<Long, String> roomNames = rooms.findAllById(
                        list.stream().map(Lesson::getRoomId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Room::getId, Room::getName));
        Map<Long, Long> visits = new HashMap<>();
        for (Object[] row : attendance.countByLesson(
                list.stream().map(Lesson::getId).toList(), AttendanceStatus.VISITS)) {
            visits.put((Long) row[0], (Long) row[1]);
        }
        return list.stream().map(l -> new LessonView(
                l.getId(), l.getGroupId(), groupNames.get(l.getGroupId()),
                l.getTeacherId(), l.getTeacherId() != null ? teacherNames.get(l.getTeacherId()) : null,
                l.getRoomId(), l.getRoomId() != null ? roomNames.get(l.getRoomId()) : null,
                time.localDate(l.getStartsAt()), time.localTime(l.getStartsAt()), time.localTime(l.getEndsAt()),
                l.getStartsAt().toInstant(ZoneOffset.UTC),
                l.getStatus(), l.getOrigin(), l.getNote(), visits.getOrDefault(l.getId(), 0L))).toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    /** date, startTime, endTime в местном времени школы; startsAt это тот же момент в UTC */
    public record LessonView(Long id, Long groupId, String groupName, Long teacherId, String teacherName,
                             Long roomId, String roomName, LocalDate date, LocalTime startTime, LocalTime endTime,
                             Instant startsAt, LessonStatus status, LessonOrigin origin, String note,
                             long visits) {
    }
}
