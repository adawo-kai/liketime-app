package com.liketime.device;

import com.liketime.device.DeviceEventService.EventView;
import com.liketime.device.DeviceEventService.ReprocessResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Журнал событий устройств для персонала. */
@RestController
@RequestMapping("/api/device-events")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class DeviceEventController {

    private final DeviceEventService service;

    public DeviceEventController(DeviceEventService service) {
        this.service = service;
    }

    @GetMapping
    public List<EventView> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) EventMatchStatus status,
            @RequestParam(defaultValue = "200") int limit) {
        return service.list(from, to, status, limit);
    }

    /** Разобрать заново события, которые не удалось сопоставить (по умолчанию неизвестные лица и без занятия) */
    @PostMapping("/reprocess")
    public ReprocessResult reprocess(@Valid @RequestBody ReprocessRequest request) {
        Set<EventMatchStatus> statuses = request.statuses() == null || request.statuses().isEmpty()
                ? EnumSet.of(EventMatchStatus.UNKNOWN_PERSON, EventMatchStatus.NO_LESSON)
                : EnumSet.copyOf(request.statuses());
        return service.reprocess(request.from(), request.to(), statuses);
    }

    public record ReprocessRequest(@NotNull LocalDate from, @NotNull LocalDate to, List<EventMatchStatus> statuses) {
    }
}
