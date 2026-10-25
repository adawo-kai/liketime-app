package com.liketime.auth;

import com.liketime.auth.UserAdminService.UserView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    private final UserAdminService service;

    public UserAdminController(UserAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@Valid @RequestBody CreateUserRequest request, Authentication authentication) {
        return service.create(request, authentication.getName());
    }

    @PatchMapping("/{id}")
    public UserView update(@PathVariable Long id, @RequestBody UpdateUserRequest request,
                           Authentication authentication) {
        return service.update(id, request, authentication.getName());
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordRequest request,
                              Authentication authentication) {
        service.resetPassword(id, request.password(), authentication.getName());
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "[a-z0-9._-]+") String login,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotBlank @Size(max = 150) String fullName,
            @NotNull Role role) {
    }

    public record UpdateUserRequest(Role role, Boolean enabled) {
    }

    public record PasswordRequest(@NotBlank @Size(min = 10, max = 72) String password) {
    }
}
