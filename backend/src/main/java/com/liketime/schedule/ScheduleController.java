package com.liketime.schedule;

import com.liketime.schedule.ScheduleService.GenerateResult;
import com.liketime.schedule.ScheduleService.SlotView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Шаблоны расписания: чтение у всех (преподаватель видит свои группы), изменение у ADMIN и MANAGER. */
@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private static final String STAFF = "hasAnyRole('ADMIN','MANAGER')";

    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    @GetMapping("/slots")
    public List<SlotView> slots(@RequestParam Long groupId, Authentication authentication) {
        return service.slotsOf(groupId, authentication);
    }

    @PostMapping("/slots")
    @PreAuthorize(STAFF)
    @ResponseStatus(HttpStatus.CREATED)
    public SlotView create(@Valid @RequestBody SlotRequest request, Authentication authentication) {
        return service.createSlot(request, authentication);
    }

    @PutMapping("/slots/{id}")
    @PreAuthorize(STAFF)
    public SlotView update(@PathVariable Long id, @Valid @RequestBody SlotRequest request,
                           Authentication authentication) {
        return service.updateSlot(id, request, authentication);
    }

    @DeleteMapping("/slots/{id}")
    @PreAuthorize(STAFF)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id, Authentication authentication) {
        service.setActive(id, false, authentication);
    }

    @PutMapping("/slots/{id}/activate")
    @PreAuthorize(STAFF)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@PathVariable Long id, Authentication authentication) {
        service.setActive(id, true, authentication);
    }

    @PostMapping("/generate")
    @PreAuthorize(STAFF)
    public GenerateResult generate(@Valid @RequestBody GenerateRequest request, Authentication authentication) {
        return service.generate(request, authentication);
    }

    public record SlotRequest(
            @NotNull Long groupId,
            @NotNull @Min(1) @Max(7) Integer weekday,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            Long roomId) {
    }

    public record GenerateRequest(@NotNull LocalDate from, @NotNull LocalDate to, Long groupId) {
    }
}
