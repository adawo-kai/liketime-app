package com.liketime.schedule;

import com.liketime.schedule.LessonService.LessonView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lessons")
public class LessonController {

    private static final String STAFF = "hasAnyRole('ADMIN','MANAGER')";

    private final LessonService service;

    public LessonController(LessonService service) {
        this.service = service;
    }

    @GetMapping
    public List<LessonView> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long teacherId,
            Authentication authentication) {
        return service.list(from, to, groupId, teacherId, authentication);
    }

    @GetMapping("/{id}")
    public LessonView get(@PathVariable Long id, Authentication authentication) {
        return service.get(id, authentication);
    }

    @PostMapping
    @PreAuthorize(STAFF)
    @ResponseStatus(HttpStatus.CREATED)
    public LessonView create(@Valid @RequestBody LessonCreateRequest request, Authentication authentication) {
        return service.create(request, authentication);
    }

    @PutMapping("/{id}")
    @PreAuthorize(STAFF)
    public LessonView update(@PathVariable Long id, @Valid @RequestBody LessonUpdateRequest request,
                             Authentication authentication) {
        return service.update(id, request, authentication);
    }

    @PutMapping("/{id}/status")
    public LessonView changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request,
                                   Authentication authentication) {
        return service.changeStatus(id, request.status(), authentication);
    }

    public record LessonCreateRequest(
            @NotNull Long groupId,
            @NotNull LocalDate date,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            Long teacherId,
            Long roomId,
            @Size(max = 255) String note) {
    }

    public record LessonUpdateRequest(
            @NotNull LocalDate date,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            Long teacherId,
            Long roomId,
            @Size(max = 255) String note) {
    }

    public record StatusRequest(@NotNull LessonStatus status) {
    }
}
