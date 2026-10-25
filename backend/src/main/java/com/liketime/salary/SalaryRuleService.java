package com.liketime.salary;

import com.liketime.audit.AuditService;
import com.liketime.common.Status;
import com.liketime.group.StudyGroup;
import com.liketime.group.StudyGroupRepository;
import com.liketime.salary.SalaryRuleController.RuleCreateRequest;
import com.liketime.salary.SalaryRuleController.RuleUpdateRequest;
import com.liketime.teacher.Teacher;
import com.liketime.teacher.TeacherRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class SalaryRuleService {

    private final SalaryRuleRepository rules;
    private final TeacherRepository teachers;
    private final StudyGroupRepository groups;
    private final AuditService audit;

    public SalaryRuleService(SalaryRuleRepository rules, TeacherRepository teachers, StudyGroupRepository groups,
                             AuditService audit) {
        this.rules = rules;
        this.teachers = teachers;
        this.groups = groups;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RuleView> list() {
        List<SalaryRule> all = rules.findAllSorted();
        Map<Long, String> teacherNames = teachers.findAll().stream()
                .collect(Collectors.toMap(Teacher::getId, Teacher::getFullName));
        Map<Long, String> groupNames = groups.findAll().stream()
                .collect(Collectors.toMap(StudyGroup::getId, StudyGroup::getName));
        return all.stream().map(r -> RuleView.from(r,
                r.getTeacherId() != null ? teacherNames.get(r.getTeacherId()) : null,
                r.getGroupId() != null ? groupNames.get(r.getGroupId()) : null)).toList();
    }

    public RuleView create(RuleCreateRequest r, Authentication auth) {
        if (r.teacherId() != null) {
            Teacher teacher = teachers.findById(r.teacherId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Преподаватель не найден"));
            if (teacher.getStatus() != Status.ACTIVE) {
                throw conflict("Нельзя создать правило для неактивного преподавателя");
            }
        }
        if (r.groupId() != null && !groups.existsById(r.groupId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Группа не найдена");
        }
        validateDates(r.effectiveFrom(), r.effectiveTo());
        checkOverlap(r.teacherId(), r.groupId(), r.effectiveFrom(), r.effectiveTo(), null);
        SalaryRule saved = rules.save(new SalaryRule(r.teacherId(), r.groupId(), money(r.amount()),
                r.effectiveFrom(), r.effectiveTo(), blankToNull(r.comment())));
        audit.record(auth, "SALARY_RULE_CREATE", "salary_rule", saved.getId(), describe(saved));
        return view(saved);
    }

    public RuleView update(Long id, RuleUpdateRequest r, Authentication auth) {
        SalaryRule rule = find(id);
        validateDates(r.effectiveFrom(), r.effectiveTo());
        if (rule.isActive()) {
            checkOverlap(rule.getTeacherId(), rule.getGroupId(), r.effectiveFrom(), r.effectiveTo(), id);
        }
        Map<String, Object> before = describe(rule);
        rule.update(money(r.amount()), r.effectiveFrom(), r.effectiveTo(), blankToNull(r.comment()));
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("before", before);
        details.put("after", describe(rule));
        audit.record(auth, "SALARY_RULE_UPDATE", "salary_rule", id, details);
        return view(rule);
    }

    public RuleView setActive(Long id, boolean active, Authentication auth) {
        SalaryRule rule = find(id);
        if (rule.isActive() != active) {
            if (active) {
                checkOverlap(rule.getTeacherId(), rule.getGroupId(), rule.getEffectiveFrom(),
                        rule.getEffectiveTo(), id);
            }
            rule.setActive(active);
            audit.record(auth, active ? "SALARY_RULE_ACTIVATE" : "SALARY_RULE_DEACTIVATE", "salary_rule", id,
                    describe(rule));
        }
        return view(rule);
    }

    /**
     * Выбирает правило для занятия: из подходящих по дате, преподавателю и группе берётся самое
     * конкретное (группа важнее преподавателя), при равенстве то, что действует с более поздней даты.
     */
    public static Optional<SalaryRule> pick(List<SalaryRule> candidates, Long teacherId, Long groupId, LocalDate date) {
        return candidates.stream()
                .filter(r -> r.appliesTo(teacherId, groupId, date))
                .max(Comparator.comparingInt(SalaryRule::specificity)
                        .thenComparing(SalaryRule::getEffectiveFrom)
                        .thenComparing(SalaryRule::getId));
    }

    /** Правила одной области не должны перекрываться по датам, иначе неясно, какое применять */
    private void checkOverlap(Long teacherId, Long groupId, LocalDate from, LocalDate to, Long excludeId) {
        for (SalaryRule other : rules.findActiveInScope(teacherId, groupId)) {
            if (other.getId().equals(excludeId)) {
                continue;
            }
            boolean startsBeforeOtherEnds = other.getEffectiveTo() == null || !from.isAfter(other.getEffectiveTo());
            boolean endsAfterOtherStarts = to == null || !to.isBefore(other.getEffectiveFrom());
            if (startsBeforeOtherEnds && endsAfterOtherStarts) {
                throw conflict("Для этой области уже есть правило, действующее в эти даты (с "
                        + other.getEffectiveFrom() + (other.getEffectiveTo() != null
                        ? " по " + other.getEffectiveTo() : " без даты окончания")
                        + "). Завершите его или измените даты");
            }
        }
    }

    private static void validateDates(LocalDate from, LocalDate to) {
        if (to != null && to.isBefore(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата окончания раньше даты начала");
        }
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private SalaryRule find(Long id) {
        return rules.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Правило не найдено"));
    }

    private RuleView view(SalaryRule r) {
        String teacher = r.getTeacherId() != null ? teachers.findById(r.getTeacherId())
                .map(Teacher::getFullName).orElse(null) : null;
        String group = r.getGroupId() != null ? groups.findById(r.getGroupId())
                .map(StudyGroup::getName).orElse(null) : null;
        return RuleView.from(r, teacher, group);
    }

    private static Map<String, Object> describe(SalaryRule r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("teacherId", r.getTeacherId());
        m.put("groupId", r.getGroupId());
        m.put("amount", r.getAmount().toPlainString());
        m.put("from", r.getEffectiveFrom().toString());
        m.put("to", r.getEffectiveTo() != null ? r.getEffectiveTo().toString() : null);
        m.put("active", r.isActive());
        return m;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record RuleView(Long id, Long teacherId, String teacherName, Long groupId, String groupName,
                           RuleType type, BigDecimal amount, LocalDate effectiveFrom, LocalDate effectiveTo,
                           boolean active, String comment) {
        static RuleView from(SalaryRule r, String teacherName, String groupName) {
            return new RuleView(r.getId(), r.getTeacherId(), teacherName, r.getGroupId(), groupName,
                    r.getRuleType(), r.getAmount(), r.getEffectiveFrom(), r.getEffectiveTo(), r.isActive(),
                    r.getComment());
        }
    }
}
