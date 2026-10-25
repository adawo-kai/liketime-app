package com.liketime.common;

import com.liketime.auth.UserAccountRepository;
import com.liketime.teacher.Teacher;
import com.liketime.teacher.TeacherRepository;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/** Кто делает запрос: персонал (ADMIN, MANAGER) видит всё, преподаватель только своё. */
@Component
public class AccessScope {

    private final UserAccountRepository users;
    private final TeacherRepository teachers;

    public AccessScope(UserAccountRepository users, TeacherRepository teachers) {
        this.users = users;
        this.teachers = teachers;
    }

    public boolean isStaff(Authentication auth) {
        return hasAuthority(auth, "ROLE_ADMIN") || hasAuthority(auth, "ROLE_MANAGER");
    }

    public boolean isAdmin(Authentication auth) {
        return hasAuthority(auth, "ROLE_ADMIN");
    }

    /** Преподаватель, к которому привязана учётная запись, если она есть */
    public Optional<Long> currentTeacherId(Authentication auth) {
        return users.findByLogin(auth.getName())
                .flatMap(u -> teachers.findByUserId(u.getId()))
                .map(Teacher::getId);
    }

    private static boolean hasAuthority(Authentication auth, String authority) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
