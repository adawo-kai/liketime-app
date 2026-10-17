package com.liketime.auth;

import com.liketime.auth.UserAdminController.CreateUserRequest;
import com.liketime.auth.UserAdminController.UpdateUserRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class UserAdminService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(UserAccountRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserView> list() {
        return users.findAll(Sort.by("login")).stream().map(UserView::from).toList();
    }

    public UserView create(CreateUserRequest request) {
        if (users.existsByLogin(request.login())) {
            throw conflict("Пользователь с таким логином уже есть");
        }
        UserAccount user = new UserAccount(
                request.login(),
                passwordEncoder.encode(request.password()),
                request.fullName(),
                request.role());
        return UserView.from(users.save(user));
    }

    /**
     * Меняет роль и/или активность. Администратор не может отключить или понизить себя,
     * и в системе всегда должен оставаться хотя бы один активный администратор.
     */
    public UserView update(Long id, UpdateUserRequest request, String actorLogin) {
        UserAccount user = find(id);
        Role newRole = request.role() != null ? request.role() : user.getRole();
        boolean newEnabled = request.enabled() != null ? request.enabled() : user.isEnabled();

        boolean losesAdminAccess = newRole != Role.ADMIN || !newEnabled;
        if (user.getLogin().equals(actorLogin) && losesAdminAccess) {
            throw conflict("Нельзя отключить или понизить свою учётную запись");
        }
        boolean isActiveAdmin = user.getRole() == Role.ADMIN && user.isEnabled();
        if (isActiveAdmin && losesAdminAccess && users.countByRoleAndEnabled(Role.ADMIN, true) <= 1) {
            throw conflict("Должен остаться хотя бы один активный администратор");
        }

        if (user.getRole() != newRole) {
            user.changeRole(newRole);
        }
        if (user.isEnabled() != newEnabled) {
            user.setEnabled(newEnabled);
        }
        return UserView.from(user);
    }

    public void resetPassword(Long id, String newPassword) {
        UserAccount user = find(id);
        user.changePasswordHash(passwordEncoder.encode(newPassword));
    }

    private UserAccount find(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден"));
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record UserView(Long id, String login, String fullName, Role role, boolean enabled,
                           LocalDateTime createdAt) {
        static UserView from(UserAccount u) {
            return new UserView(u.getId(), u.getLogin(), u.getFullName(), u.getRole(), u.isEnabled(),
                    u.getCreatedAt());
        }
    }
}
