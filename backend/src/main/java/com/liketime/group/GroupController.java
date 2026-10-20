package com.liketime.group;

import com.liketime.group.GroupService.GroupView;
import com.liketime.group.GroupService.MemberView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Чтение доступно всем ролям (преподаватель видит только свои группы), изменение только ADMIN и MANAGER.
 */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private static final String STAFF = "hasAnyRole('ADMIN','MANAGER')";

    private final GroupService service;

    public GroupController(GroupService service) {
        this.service = service;
    }

    @GetMapping
    public List<GroupView> list(Authentication authentication) {
        return service.list(authentication);
    }

    @GetMapping("/{id}")
    public GroupView get(@PathVariable Long id, Authentication authentication) {
        return service.get(id, authentication);
    }

    @PostMapping
    @PreAuthorize(STAFF)
    @ResponseStatus(HttpStatus.CREATED)
    public GroupView create(@Valid @RequestBody GroupRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize(STAFF)
    public GroupView update(@PathVariable Long id, @Valid @RequestBody GroupRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(STAFF)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable Long id) {
        service.archive(id);
    }

    @PutMapping("/{id}/restore")
    @PreAuthorize(STAFF)
    public GroupView restore(@PathVariable Long id) {
        return service.restore(id);
    }

    @GetMapping("/{id}/students")
    public List<MemberView> members(@PathVariable Long id,
                                    @RequestParam(defaultValue = "true") boolean activeOnly,
                                    Authentication authentication) {
        return service.members(id, activeOnly, authentication);
    }

    @PostMapping("/{id}/students")
    @PreAuthorize(STAFF)
    @ResponseStatus(HttpStatus.CREATED)
    public MemberView addStudent(@PathVariable Long id, @Valid @RequestBody AddStudentRequest request) {
        return service.addStudent(id, request);
    }

    @DeleteMapping("/{id}/students/{studentId}")
    @PreAuthorize(STAFF)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeStudent(@PathVariable Long id, @PathVariable Long studentId) {
        service.removeStudent(id, studentId);
    }

    public record GroupRequest(@NotBlank @Size(max = 100) String name, Long teacherId) {
    }

    public record AddStudentRequest(@NotNull Long studentId, LocalDate joinedOn) {
    }
}
