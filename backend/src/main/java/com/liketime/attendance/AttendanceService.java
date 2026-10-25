package com.liketime.attendance;

import com.liketime.audit.AuditService;
import com.liketime.common.AccessScope;
import com.liketime.common.AppTime;
import com.liketime.common.PeriodLock;
import com.liketime.group.GroupMembership;
import com.liketime.group.GroupMembershipRepository;
import com.liketime.schedule.Lesson;
import com.liketime.schedule.LessonRepository;
import com.liketime.schedule.LessonStatus;
import com.liketime.student.Student;
import com.liketime.student.StudentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AttendanceService {

    /** Что произошло с отметкой при событии устройства */
    public enum DeviceOutcome { RECORDED, UPDATED, KEPT_MANUAL, LOCKED }

    private final AttendanceRepository attendance;
    private final LessonRepository lessons;
    private final GroupMembershipRepository memberships;
    private final StudentRepository students;
    private final AccessScope access;
    private final AppTime time;
    private final AuditService audit;
    private final PeriodLock periodLock;
    private final AttendanceProperties properties;

    public AttendanceService(AttendanceRepository attendance, LessonRepository lessons,
                             GroupMembershipRepository memberships, StudentRepository students,
                             AccessScope access, AppTime time, AuditService audit, PeriodLock periodLock,
                             AttendanceProperties properties) {
        this.attendance = attendance;
        this.lessons = lessons;
        this.memberships = memberships;
        this.students = students;
        this.access = access;
        this.time = time;
        this.audit = audit;
        this.periodLock = periodLock;
        this.properties = properties;
    }

    /** Ученики занятия: состав группы на дату плюс все, у кого уже есть отметка */
    @Transactional(readOnly = true)
    public Roster roster(Long lessonId, Authentication auth) {
        Lesson lesson = findAccessible(lessonId, auth);
        LocalDate date = time.localDate(lesson.getStartsAt());
        Map<Long, Attendance> marks = attendance.findByLessonId(lessonId).stream()
                .collect(Collectors.toMap(Attendance::getStudentId, Function.identity()));
        Set<Long> memberIds = memberships.findByGroupIdOn(lesson.getGroupId(), date).stream()
                .map(GroupMembership::getStudentId).collect(Collectors.toSet());

        Set<Long> all = new java.util.HashSet<>(memberIds);
        all.addAll(marks.keySet());
        Map<Long, Student> byId = students.findAllById(all).stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));

        List<Row> rows = all.stream()
                .map(id -> row(byId.get(id), marks.get(id), memberIds.contains(id)))
                .sorted(Comparator.comparing(Row::fullName))
                .toList();
        return new Roster(lesson.getId(), lesson.getStatus(), rows);
    }

    public Row mark(Long lessonId, Long studentId, MarkInput input, Authentication auth) {
        Lesson lesson = findAccessible(lessonId, auth);
        requireWritable(lesson, auth);
        Row row = applyManual(lesson, check(lesson, studentId), input, auth);
        promoteToHeld(lesson, auth);
        return row;
    }

    /**
     * Отметка нескольких учеников одной операцией: всё или ничего. Сначала проверяются все ученики,
     * и только если ошибок нет, отметки записываются.
     */
    public List<Row> markAll(Long lessonId, List<BulkInput> items, Authentication auth) {
        Lesson lesson = findAccessible(lessonId, auth);
        requireWritable(lesson, auth);
        List<Checked> checked = items.stream().map(item -> check(lesson, item.studentId())).toList();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            BulkInput item = items.get(i);
            rows.add(applyManual(lesson, checked.get(i), new MarkInput(item.status(), item.note()), auth));
        }
        promoteToHeld(lesson, auth);
        return rows;
    }

    /** Снять отметку (например, поставленную по ошибке) */
    public void clear(Long lessonId, Long studentId, Authentication auth) {
        Lesson lesson = findAccessible(lessonId, auth);
        requireWritable(lesson, auth);
        Attendance existing = attendance.findByLessonIdAndStudentId(lessonId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Отметки нет"));
        audit.record(auth, "ATTENDANCE_CLEAR", "lesson", lessonId,
                Map.of("studentId", studentId, "status", existing.getStatus().name(),
                        "source", existing.getSource().name()));
        attendance.delete(existing);
    }

    /**
     * Приход ученика по событию устройства. Ручная отметка не перезаписывается; повторные срабатывания
     * не меняют статус, но более раннее время прихода (события могут прийти не по порядку) исправляет его.
     */
    public DeviceOutcome applyDeviceCheckIn(Lesson lesson, Long studentId, LocalDateTime at) {
        if (periodLock.isLocked(time.localDate(lesson.getStartsAt()))) {
            return DeviceOutcome.LOCKED;
        }
        Optional<Attendance> existing = attendance.findByLessonIdAndStudentId(lesson.getId(), studentId);
        if (existing.isPresent() && existing.get().getSource() == AttendanceSource.MANUAL) {
            return DeviceOutcome.KEPT_MANUAL;
        }
        if (existing.isPresent() && existing.get().getCheckInAt() != null
                && !at.isBefore(existing.get().getCheckInAt())) {
            return DeviceOutcome.UPDATED;
        }
        AttendanceStatus status = at.isAfter(lesson.getStartsAt().plusMinutes(properties.lateAfterMinutes()))
                ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;
        Attendance record = existing.orElseGet(() -> new Attendance(lesson.getId(), studentId));
        record.markByDevice(status, at);
        attendance.save(record);
        audit.record(AuditService.SYSTEM, "ATTENDANCE_DEVICE", "lesson", lesson.getId(),
                Map.of("studentId", studentId, "status", status.name(), "checkInAt", at.toString()));
        if (lesson.getStatus() == LessonStatus.PLANNED && lesson.getTeacherId() != null) {
            lesson.setStatus(LessonStatus.HELD);
        }
        return existing.isPresent() ? DeviceOutcome.UPDATED : DeviceOutcome.RECORDED;
    }

    /** Ученик найден и может быть отмечен: состоял в группе в день занятия или уже имеет отметку */
    private Checked check(Lesson lesson, Long studentId) {
        Student student = students.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ученик не найден"));
        Optional<Attendance> existing = attendance.findByLessonIdAndStudentId(lesson.getId(), studentId);
        LocalDate date = time.localDate(lesson.getStartsAt());
        boolean member = memberships.findByGroupIdOn(lesson.getGroupId(), date).stream()
                .anyMatch(m -> m.getStudentId().equals(studentId));
        if (!member && existing.isEmpty()) {
            throw conflict("Ученик «" + student.getFullName() + "» не состоял в группе в день занятия");
        }
        return new Checked(student, existing, member);
    }

    private Row applyManual(Lesson lesson, Checked checked, MarkInput input, Authentication auth) {
        Student student = checked.student();
        Long studentId = student.getId();
        Optional<Attendance> existing = checked.existing();
        boolean member = checked.member();
        String note = input.note() == null || input.note().isBlank() ? null : input.note().trim();
        Attendance record = existing.orElseGet(() -> new Attendance(lesson.getId(), studentId));
        String before = existing.map(a -> a.getStatus().name()).orElse(null);
        record.markManually(input.status(), note, auth.getName());
        attendance.save(record);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("studentId", studentId);
        details.put("before", before);
        details.put("after", input.status().name());
        audit.record(auth, "ATTENDANCE_MARK", "lesson", lesson.getId(), details);
        return row(student, record, member);
    }

    /** Если ученики пришли, занятие считается проведённым (при условии, что назначен преподаватель) */
    private void promoteToHeld(Lesson lesson, Authentication auth) {
        if (lesson.getStatus() == LessonStatus.PLANNED && lesson.getTeacherId() != null
                && attendance.existsByLessonIdAndStatusIn(lesson.getId(), AttendanceStatus.VISITS)) {
            lesson.setStatus(LessonStatus.HELD);
            audit.record(auth, "LESSON_HELD", "lesson", lesson.getId(), Map.of("reason", "attendance"));
        }
    }

    private void requireWritable(Lesson lesson, Authentication auth) {
        if (lesson.getStatus() == LessonStatus.CANCELLED) {
            throw conflict("Занятие отменено, посещаемость не ведётся");
        }
        if (time.nowUtc().isBefore(lesson.getStartsAt().minusMinutes(properties.earlyMinutes()))) {
            throw conflict("Занятие ещё не началось");
        }
        LocalDate date = time.localDate(lesson.getStartsAt());
        periodLock.assertEditable(date);
        if (!access.isStaff(auth) && date.isBefore(time.today().minusDays(properties.teacherEditDays()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Преподаватель может менять отметки не позже " + properties.teacherEditDays()
                            + " дн. после занятия. Обратитесь к администратору");
        }
    }

    /** Чужое занятие для преподавателя выглядит как несуществующее */
    private Lesson findAccessible(Long lessonId, Authentication auth) {
        Lesson lesson = lessons.findById(lessonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Занятие не найдено"));
        if (access.isStaff(auth)) {
            return lesson;
        }
        boolean own = access.currentTeacherId(auth).filter(t -> t.equals(lesson.getTeacherId())).isPresent();
        if (!own) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Занятие не найдено");
        }
        return lesson;
    }

    private static Row row(Student student, Attendance record, boolean member) {
        Instant checkIn = record != null && record.getCheckInAt() != null
                ? record.getCheckInAt().toInstant(ZoneOffset.UTC) : null;
        return new Row(student.getId(), student.getFullName(),
                record != null ? record.getStatus() : null,
                record != null ? record.getSource() : null,
                checkIn, record != null ? record.getNote() : null, member);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record Checked(Student student, Optional<Attendance> existing, boolean member) {
    }

    public record MarkInput(AttendanceStatus status, String note) {
    }

    public record BulkInput(Long studentId, AttendanceStatus status, String note) {
    }

    /** status = null означает, что ученик ещё не отмечен; member = false: ученик уже вышел из группы */
    public record Row(Long studentId, String fullName, AttendanceStatus status, AttendanceSource source,
                      Instant checkInAt, String note, boolean member) {
    }

    public record Roster(Long lessonId, LessonStatus lessonStatus, List<Row> students) {
    }
}
