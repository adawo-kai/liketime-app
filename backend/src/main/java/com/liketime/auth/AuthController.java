package com.liketime.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserAccountRepository users;

    public AuthController(AuthService authService, UserAccountRepository users) {
        this.authService = authService;
        this.users = users;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request.login(), request.password(), http.getRemoteAddr());
    }

    /** Смена своего пароля; в ответе новый токен, прежние токены перестают действовать */
    @PutMapping("/password")
    public TokenResponse changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                        Authentication authentication, HttpServletRequest http) {
        return authService.changePassword(authentication.getName(), request.currentPassword(),
                request.newPassword(), http.getRemoteAddr());
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        UserAccount user = users.findByLogin(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new MeResponse(user.getId(), user.getLogin(), user.getFullName(), user.getRole());
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record LoginRequest(@NotBlank String login, @NotBlank String password) {
    }

    public record MeResponse(Long id, String login, String fullName, Role role) {
    }
}
