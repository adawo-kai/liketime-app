package com.liketime.salary;

import com.liketime.common.AccessScope;
import com.liketime.group.StudyGroup;
import com.liketime.group.StudyGroupRepository;
import com.liketime.teacher.Teacher;
import com.liketime.teacher.TeacherRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Просмотр зарплатных периодов и выгрузки. Преподаватель видит только свою зарплату по утверждённым периодам. */
@Service
@Transactional(readOnly = true)
public class SalaryReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final SalaryPeriodRepository periods;
    private final SalaryLineRepository lines;
    private final SalaryAdjustmentRepository adjustments;
    private final TeacherRepository teachers;
    private final StudyGroupRepository groups;
    private final AccessScope access;

    public SalaryReportService(SalaryPeriodRepository periods, SalaryLineRepository lines,
                               SalaryAdjustmentRepository adjustments, TeacherRepository teachers,
                               StudyGroupRepository groups, AccessScope access) {
        this.periods = periods;
        this.lines = lines;
        this.adjustments = adjustments;
        this.teachers = teachers;
        this.groups = groups;
        this.access = access;
    }

    public List<PeriodView> periods() {
        Map<Long, BigDecimal> accrued = sums(lines.totalsByPeriod());
        Map<Long, BigDecimal> adjusted = sums(adjustments.totalsByPeriod());
        return periods.findAllByOrderByStartsOnDesc().stream()
                .map(p -> PeriodView.of(p, accrued.getOrDefault(p.getId(), ZERO), adjusted.getOrDefault(p.getId(), ZERO)))
                .toList();
    }

    public PeriodView view(SalaryPeriod p) {
        BigDecimal accrued = lines.totalsByTeacher(p.getId()).stream()
                .map(r -> (BigDecimal) r[3]).reduce(ZERO, BigDecimal::add);
        BigDecimal adjusted = adjustments.totalsByTeacher(p.getId()).stream()
                .map(r -> (BigDecimal) r[1]).reduce(ZERO, BigDecimal::add);
        return PeriodView.of(p, accrued, adjusted);
    }

    public PeriodSummary summary(Long id) {
        SalaryPeriod period = find(id);
        Map<Long, Object[]> lineTotals = lines.totalsByTeacher(id).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> r));
        Map<Long, BigDecimal> adjusted = adjustments.totalsByTeacher(id).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (BigDecimal) r[1]));
        Set<Long> teacherIds = new HashSet<>(lineTotals.keySet());
        teacherIds.addAll(adjusted.keySet());
        Map<Long, String> names = teacherNames(teacherIds);

        List<TeacherTotal> rows = teacherIds.stream().map(teacherId -> {
            Object[] t = lineTotals.get(teacherId);
            long lessonCount = t != null ? (Long) t[1] : 0;
            long visits = t != null ? ((Number) t[2]).longValue() : 0;
            BigDecimal accrued = t != null ? (BigDecimal) t[3] : ZERO;
            BigDecimal adj = adjusted.getOrDefault(teacherId, ZERO);
            return new TeacherTotal(teacherId, names.get(teacherId), lessonCount, visits, accrued, adj,
                    accrued.add(adj));
        }).sorted(Comparator.comparing(TeacherTotal::teacherName, Comparator.nullsLast(String::compareTo))).toList();

        return new PeriodSummary(view(period), rows, lines.countByPeriodIdAndRuleIdIsNull(id));
    }

    public TeacherDetail teacherDetail(Long periodId, Long teacherId, Authentication auth) {
        SalaryPeriod period = find(periodId);
        if (!access.isStaff(auth)) {
            boolean own = access.currentTeacherId(auth).filter(t -> t.equals(teacherId)).isPresent();
            if (!own || !period.getStatus().isLocked()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Период не найден");
            }
        }
        Teacher teacher = teachers.findById(teacherId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Преподаватель не найден"));
        List<SalaryLine> found = lines.findByPeriodIdAndTeacherIdOrderByLessonDateAscIdAsc(periodId, teacherId);
        Map<Long, String> groupNames = groupNames(found.stream().map(SalaryLine::getGroupId).collect(Collectors.toSet()));
        List<LineView> lineViews = found.stream().map(l -> new LineView(l.getLessonId(), l.getLessonDate(),
                l.getGroupId(), groupNames.get(l.getGroupId()), l.getVisits(), l.getUnitAmount(), l.getAmount(),
                l.getRuleId() != null)).toList();
        List<SalaryAdjustment> adj = adjustments.findByPeriodIdAndTeacherIdOrderByIdAsc(periodId, teacherId);
        List<AdjustmentView> adjViews = adj.stream().map(AdjustmentView::from).toList();
        BigDecimal accrued = found.stream().map(SalaryLine::getAmount).reduce(ZERO, BigDecimal::add);
        BigDecimal adjusted = adj.stream().map(SalaryAdjustment::getAmount).reduce(ZERO, BigDecimal::add);
        return new TeacherDetail(periodId, period.getStartsOn(), period.getEndsOn(), period.getStatus(), teacherId,
                teacher.getFullName(), lineViews, adjViews, accrued, adjusted, accrued.add(adjusted));
    }

    /** Свои утверждённые и выплаченные периоды преподавателя с итогами */
    public List<MyPeriod> myPeriods(Authentication auth) {
        Long teacherId = access.currentTeacherId(auth).orElse(null);
        if (teacherId == null) {
            return List.of();
        }
        return periods.findAllByOrderByStartsOnDesc().stream()
                .filter(p -> p.getStatus().isLocked())
                .map(p -> {
                    BigDecimal accrued = lines.findByPeriodIdAndTeacherIdOrderByLessonDateAscIdAsc(p.getId(), teacherId)
                            .stream().map(SalaryLine::getAmount).reduce(ZERO, BigDecimal::add);
                    BigDecimal adjusted = adjustments.findByPeriodIdAndTeacherIdOrderByIdAsc(p.getId(), teacherId)
                            .stream().map(SalaryAdjustment::getAmount).reduce(ZERO, BigDecimal::add);
                    return new MyPeriod(p.getId(), p.getStartsOn(), p.getEndsOn(), p.getStatus(), teacherId,
                            accrued, adjusted, accrued.add(adjusted));
                }).toList();
    }

    public CsvFile summaryCsv(Long id) {
        PeriodSummary summary = summary(id);
        PeriodView p = summary.period();
        CsvFile csv = new CsvFile("salary-" + p.startsOn() + "_" + p.endsOn() + ".csv");
        csv.row("Период", p.startsOn() + " - " + p.endsOn(), "Статус", p.status());
        csv.row("Преподаватель", "Занятий", "Приходов", "Начислено", "Корректировки", "К выплате");
        for (TeacherTotal t : summary.teachers()) {
            csv.row(t.teacherName(), t.lessons(), t.visits(), t.accrued(), t.adjustments(), t.total());
        }
        csv.row("Итого", "", "", p.accrued(), p.adjustments(), p.total());
        return csv;
    }

    public CsvFile detailsCsv(Long id) {
        SalaryPeriod period = find(id);
        List<SalaryLine> all = lines.findByPeriodIdOrderByLessonDateAscIdAsc(id);
        List<SalaryAdjustment> adj = adjustments.findByPeriodIdOrderByIdAsc(id);
        Set<Long> teacherIds = all.stream().map(SalaryLine::getTeacherId).collect(Collectors.toCollection(HashSet::new));
        adj.forEach(a -> teacherIds.add(a.getTeacherId()));
        Map<Long, String> names = teacherNames(teacherIds);
        Map<Long, String> groupNames = groupNames(all.stream().map(SalaryLine::getGroupId).collect(Collectors.toSet()));

        CsvFile csv = new CsvFile("salary-details-" + period.getStartsOn() + "_" + period.getEndsOn() + ".csv");
        csv.row("Преподаватель", "Тип", "Дата", "Описание", "Приходов", "Ставка", "Сумма");
        teacherIds.stream().sorted(Comparator.comparing(names::get)).forEach(teacherId -> {
            for (SalaryLine l : all) {
                if (l.getTeacherId().equals(teacherId)) {
                    csv.row(names.get(teacherId), "Начисление", l.getLessonDate().toString(),
                            groupNames.get(l.getGroupId()), l.getVisits(), l.getUnitAmount(), l.getAmount());
                }
            }
            for (SalaryAdjustment a : adj) {
                if (a.getTeacherId().equals(teacherId)) {
                    csv.row(names.get(teacherId), "Корректировка", "", a.getReason(), "", "", a.getAmount());
                }
            }
        });
        return csv;
    }

    private SalaryPeriod find(Long id) {
        return periods.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Период не найден"));
    }

    private Map<Long, String> teacherNames(Collection<Long> ids) {
        return teachers.findAllById(ids).stream().collect(Collectors.toMap(Teacher::getId, Teacher::getFullName));
    }

    private Map<Long, String> groupNames(Collection<Long> ids) {
        return groups.findAllById(ids).stream().collect(Collectors.toMap(StudyGroup::getId, StudyGroup::getName));
    }

    private static Map<Long, BigDecimal> sums(List<Object[]> rows) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (BigDecimal) row[1]);
        }
        return map;
    }

    private static Instant instant(LocalDateTime t) {
        return t != null ? t.toInstant(ZoneOffset.UTC) : null;
    }

    public record PeriodView(Long id, LocalDate startsOn, LocalDate endsOn, PeriodStatus status,
                             Instant calculatedAt, Instant approvedAt, String approvedBy, Instant paidAt,
                             String paidBy, BigDecimal accrued, BigDecimal adjustments, BigDecimal total) {
        static PeriodView of(SalaryPeriod p, BigDecimal accrued, BigDecimal adjustments) {
            return new PeriodView(p.getId(), p.getStartsOn(), p.getEndsOn(), p.getStatus(),
                    instant(p.getCalculatedAt()), instant(p.getApprovedAt()), p.getApprovedBy(),
                    instant(p.getPaidAt()), p.getPaidBy(), accrued, adjustments, accrued.add(adjustments));
        }
    }

    public record TeacherTotal(Long teacherId, String teacherName, long lessons, long visits, BigDecimal accrued,
                               BigDecimal adjustments, BigDecimal total) {
    }

    /** linesWithoutRule > 0: есть занятия, для которых не нашлось правила оплаты (период не утвердить) */
    public record PeriodSummary(PeriodView period, List<TeacherTotal> teachers, long linesWithoutRule) {
    }

    public record LineView(Long lessonId, LocalDate date, Long groupId, String groupName, int visits,
                           BigDecimal unitAmount, BigDecimal amount, boolean hasRule) {
    }

    public record AdjustmentView(Long id, Long teacherId, BigDecimal amount, String reason, String createdBy,
                                 Instant createdAt) {
        public static AdjustmentView from(SalaryAdjustment a) {
            return new AdjustmentView(a.getId(), a.getTeacherId(), a.getAmount(), a.getReason(), a.getCreatedBy(),
                    instant(a.getCreatedAt()));
        }
    }

    public record TeacherDetail(Long periodId, LocalDate startsOn, LocalDate endsOn, PeriodStatus status,
                                Long teacherId, String teacherName, List<LineView> lines,
                                List<AdjustmentView> adjustments, BigDecimal accrued, BigDecimal adjustmentsTotal,
                                BigDecimal total) {
    }

    public record MyPeriod(Long periodId, LocalDate startsOn, LocalDate endsOn, PeriodStatus status, Long teacherId,
                           BigDecimal accrued, BigDecimal adjustments, BigDecimal total) {
    }
}
