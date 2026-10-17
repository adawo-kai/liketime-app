package com.liketime.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByLogin(String login);

    boolean existsByLogin(String login);

    long countByRoleAndEnabled(Role role, boolean enabled);
}
