package com.liketime.salary;

import com.liketime.salary.SalaryRuleService.RuleView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Правила зарплаты: смотреть могут ADMIN и MANAGER, менять только ADMIN. */
@RestController
@RequestMapping("/api/salary/rules")
public class SalaryRuleController {

    private final SalaryRuleService service;

    public SalaryRuleController(SalaryRuleService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<RuleView> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public RuleView create(@Valid @RequestBody RuleCreateRequest request, Authentication authentication) {
        return service.create(request, authentication);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RuleView update(@PathVariable Long id, @Valid @RequestBody RuleUpdateRequest request,
                           Authentication authentication) {
        return service.update(id, request, authentication);
    }

    /** Правило не удаляется, а отключается: по нему могли быть рассчитаны прошлые периоды */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RuleView deactivate(@PathVariable Long id, Authentication authentication) {
        return service.setActive(id, false, authentication);
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public RuleView activate(@PathVariable Long id, Authentication authentication) {
        return service.setActive(id, true, authentication);
    }

    public record RuleCreateRequest(
            Long teacherId,
            Long groupId,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @Size(max = 255) String comment) {
    }

    public record RuleUpdateRequest(
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @Size(max = 255) String comment) {
    }
}
