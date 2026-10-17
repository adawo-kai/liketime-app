package com.liketime.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Создаёт первого администратора, если в базе нет ни одного пользователя.
 * Логин и пароль берутся из конфигурации (application-local.properties), в коде их нет.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String login;
    private final String password;

    public AdminBootstrap(UserAccountRepository users, PasswordEncoder passwordEncoder,
                          @Value("${liketime.bootstrap.admin.login:}") String login,
                          @Value("${liketime.bootstrap.admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.login = login;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0 || login.isBlank() || password.isBlank()) {
            return;
        }
        users.save(new UserAccount(login, passwordEncoder.encode(password), "Администратор", Role.ADMIN));
        log.info("Создан первый администратор '{}'", login);
    }
}
