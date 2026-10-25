package com.liketime.salary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liketime.attendance.AttendanceRepository;
import com.liketime.attendance.AttendanceStatus;
import com.liketime.audit.AuditService;
import com.liketime.common.AppTime;
import com.liketime.salary.SalaryPeriodController.AdjustmentRequest;
import com.liketime.schedule.Lesson;
import com.liketime.schedule.LessonRepository;
import com.liketime.schedule.LessonStatus;
import com.liketime.teacher.TeacherRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Зарплатные периоды: создание, расчёт, утверждение, выплата и корректировки.
 *
 * <p>Жизненный цикл: OPEN -> CALCULATED -> APPROVED -> PAID. Утверждённый период блокирует правки
 * посещаемости и занятий за его даты (см. {@link SalaryPeriodLock}). Из APPROVED можно вернуться в
 * CALCULATED, из PAID нельзя.
 */
@Service
@Transactional
public class SalaryPeriodService {

    private static final int MAX_PERIOD_DAYS = 93;

    private final SalaryPeriodRepository periods;
    private final SalaryLineRepository lines;
    private final SalaryAdjustmentRepository adjustments;
    private final SalaryRuleRepository rules;
    private final LessonRepository lessons;
    private final AttendanceRepository attendance;
    private final TeacherRepository teachers;
    private final AppTime time;
    private final AuditService audit;
    private final ObjectMapper objectMapper;

    public SalaryPeriodService(SalaryPeriodRepository periods, SalaryLineRepository lines,
                               SalaryAdjustmentRepository adjustments, SalaryRuleRepository rules,
                               LessonRepository lessons, AttendanceRepository attendance,
                               TeacherRepository teachers, AppTime time, AuditService audit,
                               ObjectMapper objectMapper) {
        this.periods = periods;
        this.lines = lines;
        this.adjustments = adjustments;
        this.rules = rules;
        this.lessons = lessons;
        this.attendance = attendance;
        this.teachers = teachers;
        this.time = time;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    public SalaryPeriod create(LocalDate startsOn, LocalDate endsOn, Authentication auth) {
        if (endsOn.isBefore(startsOn)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата окончания раньше даты начала");
        }
        if (ChronoUnit.DAYS.between(startsOn, endsOn) + 1 > MAX_PERIOD_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Период не должен быть длиннее " + MAX_PERIOD_DAYS + " дней");
        }
        if (periods.existsOverlapping(startsOn, endsOn)) {
            throw conflict("Период пересекается с уже созданным");
        }
        SalaryPeriod saved = periods.save(new SalaryPeriod(startsOn, endsOn));
        audit.record(auth, "PERIOD_CREATE", "salary_period", saved.getId(),
                Map.of("from", startsOn.toString(), "to", endsOn.toString()));
        return saved;
    }

    /** Удалить можно только период в работе и без ручных корректировок: они вводились вручную и пропали бы */
    public void delete(Long id, Authentication auth) {
        SalaryPeriod period = find(id);
        requireNotLocked(period);
        if (adjustments.existsByPeriodId(id)) {
            throw conflict("В периоде есть корректировки. Сначала удалите их");
        }
        lines.deleteByPeriod(id);
        audit.record(auth, "PERIOD_DELETE", "salary_period", id,
                Map.of("from", period.getStartsOn().toString(), "to", period.getEndsOn().toString()));
        periods.delete(period);
    }

    public CalculationResult calculate(Long id, Authentication auth) {
        SalaryPeriod period = find(id);
        requireNotLocked(period);
        CalculationResult result = recompute(period);
        period.markCalculated(time.nowUtc());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("lines", result.lines());
        details.put("withoutRule", result.withoutRule());
        details.put("accrued", result.accrued().toPlainString());
        audit.record(auth, "PERIOD_CALCULATE", "salary_period", id, details);
        return result;
    }

    /**
     * Утверждение. Перед ним расчёт выполняется заново, чтобы утвердить актуальные данные, а не
     * устаревший расчёт. Период должен закончиться, а у всех занятий должно быть правило оплаты.
     */
    public SalaryPeriod approve(Long id, Authentication auth) {
        SalaryPeriod period = find(id);
        requireNotLocked(period);
        if (!period.getEndsOn().isBefore(time.today())) {
            throw conflict("Период ещё не закончился. Утвердить можно только после его последнего дня");
        }
        CalculationResult result = recompute(period);
        if (result.withoutRule() > 0) {
            throw conflict("Для " + result.withoutRule()
                    + " занятий нет правила оплаты. Добавьте правило и пересчитайте период");
        }
        period.markCalculated(time.nowUtc());
        period.approve(auth.getName(), time.nowUtc());
        audit.record(auth, "PERIOD_APPROVE", "salary_period", id, Map.of("accrued", result.accrued().toPlainString()));
        return period;
    }

    public SalaryPeriod pay(Long id, Authentication auth) {
        SalaryPeriod period = find(id);
        if (period.getStatus() != PeriodStatus.APPROVED) {
            throw conflict("Выплатить можно только утверждённый период");
        }
        period.markPaid(auth.getName(), time.nowUtc());
        audit.record(auth, "PERIOD_PAID", "salary_period", id, null);
        return period;
    }

    public SalaryPeriod reopen(Long id, Authentication auth) {
        SalaryPeriod period = find(id);
        if (period.getStatus() != PeriodStatus.APPROVED) {
            throw conflict(period.getStatus() == PeriodStatus.PAID
                    ? "Выплаченный период вернуть нельзя" : "Период не утверждён");
        }
        period.reopen();
        audit.record(auth, "PERIOD_REOPEN", "salary_period", id, null);
        return period;
    }

    public SalaryAdjustment addAdjustment(Long periodId, AdjustmentRequest r, Authentication auth) {
        SalaryPeriod period = find(periodId);
        requireNotLocked(period);
        if (r.amount().signum() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сумма корректировки не может быть нулевой");
        }
        if (!teachers.existsById(r.teacherId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Преподаватель не найден");
        }
        SalaryAdjustment saved = adjustments.save(new SalaryAdjustment(periodId, r.teacherId(),
                r.amount().setScale(2, java.math.RoundingMode.HALF_UP), r.reason().trim(), auth.getName(),
                time.nowUtc()));
        audit.record(auth, "ADJUSTMENT_ADD", "salary_period", periodId,
                Map.of("teacherId", r.teacherId(), "amount", saved.getAmount().toPlainString(), "reason", saved.getReason()));
        return saved;
    }

    public void deleteAdjustment(Long id, Authentication auth) {
        SalaryAdjustment adjustment = adjustments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Корректировка не найдена"));
        requireNotLocked(find(adjustment.getPeriodId()));
        audit.record(auth, "ADJUSTMENT_DELETE", "salary_period", adjustment.getPeriodId(),
                Map.of("teacherId", adjustment.getTeacherId(), "amount", adjustment.getAmount().toPlainString(),
                        "reason", adjustment.getReason()));
        adjustments.delete(adjustment);
    }

    /**
     * Пересчитывает строки начислений периода: за каждое проведённое занятие с преподавателем сумма равна
     * числу приходов (PRESENT и LATE), умноженному на ставку подходящего правила. Если правила нет,
     * строка создаётся с нулевой суммой, чтобы проблема была видна, а период нельзя было утвердить.
     */
    private CalculationResult recompute(SalaryPeriod period) {
        lines.deleteByPeriod(period.getId());
        List<Lesson> held = lessons.search(time.startOfDayUtc(period.getStartsOn()),
                        time.startOfDayUtc(period.getEndsOn().plusDays(1)), null, null).stream()
                .filter(l -> l.getStatus() == LessonStatus.HELD && l.getTeacherId() != null)
                .toList();
        if (held.isEmpty()) {
            return new CalculationResult(0, 0, BigDecimal.ZERO.setScale(2));
        }
        Map<Long, Long> visits = new HashMap<>();
        for (Object[] row : attendance.countByLesson(held.stream().map(Lesson::getId).toList(),
                AttendanceStatus.VISITS)) {
            visits.put((Long) row[0], (Long) row[1]);
        }
        List<SalaryRule> activeRules = rules.findByActiveTrue();
        LocalDateTime now = time.nowUtc();
        int count = 0;
        int withoutRule = 0;
        BigDecimal accrued = BigDecimal.ZERO.setScale(2);
        for (Lesson lesson : held) {
            long visitCount = visits.getOrDefault(lesson.getId(), 0L);
            if (visitCount == 0) {
                continue;
            }
            LocalDate date = time.localDate(lesson.getStartsAt());
            SalaryRule rule = SalaryRuleService.pick(activeRules, lesson.getTeacherId(), lesson.getGroupId(), date)
                    .orElse(null);
            BigDecimal unit = rule != null ? rule.getAmount() : BigDecimal.ZERO.setScale(2);
            SalaryLine line = new SalaryLine(period.getId(), lesson.getTeacherId(), lesson.getId(),
                    lesson.getGroupId(), date, (int) visitCount, unit, rule != null ? rule.getId() : null,
                    rule != null ? snapshot(rule) : null, now);
            lines.save(line);
            count++;
            accrued = accrued.add(line.getAmount());
            if (rule == null) {
                withoutRule++;
            }
        }
        return new CalculationResult(count, withoutRule, accrued);
    }

    private String snapshot(SalaryRule rule) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ruleId", rule.getId());
        m.put("type", rule.getRuleType().name());
        m.put("amount", rule.getAmount().toPlainString());
        m.put("teacherId", rule.getTeacherId());
        m.put("groupId", rule.getGroupId());
        m.put("from", rule.getEffectiveFrom().toString());
        m.put("to", rule.getEffectiveTo() != null ? rule.getEffectiveTo().toString() : null);
        try {
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сохранить условия правила", e);
        }
    }

    private SalaryPeriod find(Long id) {
        return periods.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Период не найден"));
    }

    private static void requireNotLocked(SalaryPeriod period) {
        if (period.getStatus().isLocked()) {
            throw conflict("Период уже утверждён. Чтобы изменить данные, верните его в расчёт");
        }
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record CalculationResult(int lines, int withoutRule, BigDecimal accrued) {
    }
}
