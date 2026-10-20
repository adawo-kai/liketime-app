package com.liketime.teacher;

import com.liketime.common.Status;
import com.liketime.teacher.TeacherService.TeacherView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

@RestController
@RequestMapping("/api/teachers")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class TeacherController {

    private final TeacherService service;

    public TeacherController(TeacherService service) {
        this.service = service;
    }

    @GetMapping
    public List<TeacherView> list(@RequestParam(required = false) Status status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    public TeacherView get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherView create(@Valid @RequestBody TeacherRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public TeacherView update(@PathVariable Long id, @Valid @RequestBody TeacherRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        service.deactivate(id);
    }

    @PutMapping("/{id}/activate")
    public TeacherView activate(@PathVariable Long id) {
        return service.activate(id);
    }

    public record TeacherRequest(
            Long userId,
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 20) String phone) {
    }
}
