package com.liketime.report;

import com.liketime.attendance.Attendance;
import com.liketime.attendance.AttendanceRepository;
import com.liketime.attendance.AttendanceStatus;
import com.liketime.common.AppTime;
import com.liketime.group.GroupMembership;
import com.liketime.group.GroupMembershipRepository;
import com.liketime.group.StudyGroup;
import com.liketime.group.StudyGroupRepository;
import com.liketime.salary.CsvFile;
import com.liketime.schedule.Lesson;
import com.liketime.schedule.LessonRepository;
import com.liketime.schedule.LessonStatus;
import com.liketime.student.Student;
import com.liketime.student.StudentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Отчёт по посещаемости за период. В отчёт входят занятия, которые уже начались и не отменены.
 * Для каждого ученика считаются занятия его групп (в те дни, когда он в них состоял) и отметки по ним.
 */
@Service
@Transactional(readOnly = true)
public class AttendanceReportService {

    private static final int MAX_DAYS = 100;

    private final LessonRepository lessons;
    private final AttendanceRepository attendance;
    private final GroupMembershipRepository memberships;
    private final StudyGroupRepository groups;
    private final StudentRepository students;
    private final AppTime time;

    public AttendanceReportService(LessonRepository lessons, AttendanceRepository attendance,
                                   GroupMembershipRepository memberships, StudyGroupRepository groups,
                                   StudentRepository students, AppTime time) {
        this.lessons = lessons;
        this.attendance = attendance;
        this.memberships = memberships;
        this.groups = groups;
        this.students = students;
        this.time = time;
    }

    public List<Row> report(LocalDate from, LocalDate to, Long groupId) {
        if (to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата окончания раньше даты начала");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Период не должен быть больше " + MAX_DAYS + " дней");
        }
        var now = time.nowUtc();
        List<Lesson> found = lessons.search(time.startOfDayUtc(from), time.startOfDayUtc(to.plusDays(1)), groupId, null)
                .stream()
                .filter(l -> l.getStatus() != LessonStatus.CANCELLED && !l.getStartsAt().isAfter(now))
                .toList();
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<Long, Attendance>> marksByLesson = attendance
                .findByLessonIdIn(found.stream().map(Lesson::getId).toList()).stream()
                .collect(Collectors.groupingBy(Attendance::getLessonId,
                        Collectors.toMap(Attendance::getStudentId, Function.identity())));

        Map<String, Counter> counters = new HashMap<>();
        Map<String, Set<Long>> memberCache = new HashMap<>();
        for (Lesson lesson : found) {
            LocalDate date = time.localDate(lesson.getStartsAt());
            Set<Long> members = memberCache.computeIfAbsent(lesson.getGroupId() + ":" + date,
                    k -> memberships.findByGroupIdOn(lesson.getGroupId(), date).stream()
                            .map(GroupMembership::getStudentId).collect(Collectors.toSet()));
            Map<Long, Attendance> marks = marksByLesson.getOrDefault(lesson.getId(), Map.of());
            Set<Long> involved = new HashSet<>(members);
            involved.addAll(marks.keySet());
            for (Long studentId : involved) {
                Counter c = counters.computeIfAbsent(lesson.getGroupId() + ":" + studentId,
                        k -> new Counter(lesson.getGroupId(), studentId));
                c.lessons++;
                Attendance mark = marks.get(studentId);
                if (mark != null) {
                    c.count(mark.getStatus());
                }
            }
        }
        Map<Long, String> groupNames = groups.findAllById(
                        counters.values().stream().map(c -> c.groupId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(StudyGroup::getId, StudyGroup::getName));
        Map<Long, String> studentNames = students.findAllById(
                        counters.values().stream().map(c -> c.studentId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Student::getId, Student::getFullName));

        return counters.values().stream()
                .map(c -> c.toRow(groupNames.get(c.groupId), studentNames.get(c.studentId)))
                .sorted(Comparator.comparing(Row::groupName).thenComparing(Row::studentName))
                .toList();
    }

    public CsvFile csv(LocalDate from, LocalDate to, Long groupId) {
        CsvFile csv = new CsvFile("attendance-" + from + "_" + to + ".csv");
        csv.row("Группа", "Ученик", "Занятий", "Пришёл", "Опоздал", "Пропустил", "Уважительная причина",
                "Не отмечено", "Посещаемость, %");
        for (Row r : report(from, to, groupId)) {
            csv.row(r.groupName(), r.studentName(), r.lessons(), r.present(), r.late(), r.absent(), r.excused(),
                    r.unmarked(), r.rate().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue());
        }
        return csv;
    }

    private static final class Counter {
        final Long groupId;
        final Long studentId;
        int lessons;
        int present;
        int late;
        int absent;
        int excused;

        Counter(Long groupId, Long studentId) {
            this.groupId = groupId;
            this.studentId = studentId;
        }

        void count(AttendanceStatus status) {
            switch (status) {
                case PRESENT -> present++;
                case LATE -> late++;
                case ABSENT -> absent++;
                case EXCUSED -> excused++;
            }
        }

        Row toRow(String groupName, String studentName) {
            int marked = present + late + absent + excused;
            BigDecimal rate = lessons == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(present + late).divide(BigDecimal.valueOf(lessons), 4, RoundingMode.HALF_UP);
            return new Row(groupId, groupName, studentId, studentName, lessons, present, late, absent, excused,
                    lessons - marked, rate);
        }
    }

    /** rate: доля занятий, на которых ученик был (пришёл или опоздал), от 0 до 1 */
    public record Row(Long groupId, String groupName, Long studentId, String studentName, int lessons, int present,
                      int late, int absent, int excused, int unmarked, BigDecimal rate) {
    }
}
