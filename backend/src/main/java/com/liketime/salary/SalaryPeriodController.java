package com.liketime.salary;

import com.liketime.salary.SalaryPeriodService.CalculationResult;
import com.liketime.salary.SalaryReportService.AdjustmentView;
import com.liketime.salary.SalaryReportService.MyPeriod;
import com.liketime.salary.SalaryReportService.PeriodSummary;
import com.liketime.salary.SalaryReportService.PeriodView;
import com.liketime.salary.SalaryReportService.TeacherDetail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Зарплатные периоды. Смотреть и выгружать могут ADMIN и MANAGER, всё остальное (создание, расчёт,
 * утверждение, выплата, корректировки) только ADMIN. Преподаватель видит свою зарплату через /my.
 */
@RestController
@RequestMapping("/api/salary")
public class SalaryPeriodController {

    private static final String STAFF = "hasAnyRole('ADMIN','MANAGER')";
    private static final String ADMIN = "hasRole('ADMIN')";

    private final SalaryPeriodService service;
    private final SalaryReportService reports;

    public SalaryPeriodController(SalaryPeriodService service, SalaryReportService reports) {
        this.service = service;
        this.reports = reports;
    }

    @GetMapping("/periods")
    @PreAuthorize(STAFF)
    public List<PeriodView> list() {
        return reports.periods();
    }

    @PostMapping("/periods")
    @PreAuthorize(ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    public PeriodView create(@Valid @RequestBody PeriodRequest request, Authentication authentication) {
        return reports.view(service.create(request.startsOn(), request.endsOn(), authentication));
    }

    @GetMapping("/periods/{id}")
    @PreAuthorize(STAFF)
    public PeriodSummary summary(@PathVariable Long id) {
        return reports.summary(id);
    }

    @DeleteMapping("/periods/{id}")
    @PreAuthorize(ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        service.delete(id, authentication);
    }

    @PostMapping("/periods/{id}/calculate")
    @PreAuthorize(ADMIN)
    public CalculationResult calculate(@PathVariable Long id, Authentication authentication) {
        return service.calculate(id, authentication);
    }

    @PostMapping("/periods/{id}/approve")
    @PreAuthorize(ADMIN)
    public PeriodView approve(@PathVariable Long id, Authentication authentication) {
        return reports.view(service.approve(id, authentication));
    }

    @PostMapping("/periods/{id}/pay")
    @PreAuthorize(ADMIN)
    public PeriodView pay(@PathVariable Long id, Authentication authentication) {
        return reports.view(service.pay(id, authentication));
    }

    @PostMapping("/periods/{id}/reopen")
    @PreAuthorize(ADMIN)
    public PeriodView reopen(@PathVariable Long id, Authentication authentication) {
        return reports.view(service.reopen(id, authentication));
    }

    @PostMapping("/periods/{id}/adjustments")
    @PreAuthorize(ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    public AdjustmentView addAdjustment(@PathVariable Long id, @Valid @RequestBody AdjustmentRequest request,
                                        Authentication authentication) {
        return AdjustmentView.from(service.addAdjustment(id, request, authentication));
    }

    @DeleteMapping("/adjustments/{id}")
    @PreAuthorize(ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAdjustment(@PathVariable Long id, Authentication authentication) {
        service.deleteAdjustment(id, authentication);
    }

    /** Подробности по преподавателю: персонал видит всегда, преподаватель только своё и только после утверждения */
    @GetMapping("/periods/{id}/teachers/{teacherId}")
    public TeacherDetail teacherDetail(@PathVariable Long id, @PathVariable Long teacherId,
                                       Authentication authentication) {
        return reports.teacherDetail(id, teacherId, authentication);
    }

    @GetMapping("/my")
    public List<MyPeriod> my(Authentication authentication) {
        return reports.myPeriods(authentication);
    }

    @GetMapping("/periods/{id}/export.csv")
    @PreAuthorize(STAFF)
    public ResponseEntity<byte[]> exportSummary(@PathVariable Long id) {
        return reports.summaryCsv(id).toResponse();
    }

    @GetMapping("/periods/{id}/export-details.csv")
    @PreAuthorize(STAFF)
    public ResponseEntity<byte[]> exportDetails(@PathVariable Long id) {
        return reports.detailsCsv(id).toResponse();
    }

    public record PeriodRequest(@NotNull LocalDate startsOn, @NotNull LocalDate endsOn) {
    }

    /** Положительная сумма это премия, отрицательная удержание */
    public record AdjustmentRequest(
            @NotNull Long teacherId,
            @NotNull @Digits(integer = 10, fraction = 2) @DecimalMin(value = "-9999999999.99") BigDecimal amount,
            @NotBlank @Size(max = 255) String reason) {
    }
}
