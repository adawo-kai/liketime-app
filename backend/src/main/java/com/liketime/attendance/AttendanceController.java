package com.liketime.attendance;

import com.liketime.attendance.AttendanceService.BulkInput;
import com.liketime.attendance.AttendanceService.MarkInput;
import com.liketime.attendance.AttendanceService.Roster;
import com.liketime.attendance.AttendanceService.Row;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Журнал посещаемости занятия. Доступ: ADMIN и MANAGER ко всем занятиям, преподаватель только к своим
 * (его ограничения по давности проверяет сервис).
 */
@RestController
@RequestMapping("/api/lessons/{lessonId}/attendance")
public class AttendanceController {

    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    @GetMapping
    public Roster roster(@PathVariable Long lessonId, Authentication authentication) {
        return service.roster(lessonId, authentication);
    }

    @PutMapping("/{studentId}")
    public Row mark(@PathVariable Long lessonId, @PathVariable Long studentId,
                    @Valid @RequestBody MarkRequest request, Authentication authentication) {
        return service.mark(lessonId, studentId, new MarkInput(request.status(), request.note()), authentication);
    }

    @PutMapping
    public List<Row> markAll(@PathVariable Long lessonId, @Valid @RequestBody BulkRequest request,
                             Authentication authentication) {
        List<BulkInput> items = request.records().stream()
                .map(r -> new BulkInput(r.studentId(), r.status(), r.note()))
                .toList();
        return service.markAll(lessonId, items, authentication);
    }

    @DeleteMapping("/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable Long lessonId, @PathVariable Long studentId, Authentication authentication) {
        service.clear(lessonId, studentId, authentication);
    }

    public record MarkRequest(@NotNull AttendanceStatus status, @Size(max = 255) String note) {
    }

    public record BulkItem(@NotNull Long studentId, @NotNull AttendanceStatus status, @Size(max = 255) String note) {
    }

    public record BulkRequest(@NotEmpty @Size(max = 200) List<@Valid BulkItem> records) {
    }
}
