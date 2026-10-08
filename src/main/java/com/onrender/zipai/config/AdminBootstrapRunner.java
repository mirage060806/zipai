package com.onrender.zipai.config;

import com.onrender.zipai.domain.ZipaiUser;
import com.onrender.zipai.repository.ZipaiUserRepository;
import com.onrender.zipai.service.ZipaiPasswordService;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "zipai.admin", name = "bootstrap-enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final ZipaiUserRepository users;
    private final ZipaiPasswordService passwords;
    private final String username;
    private final String email;
    private final String phone;
    private final String password;

    public AdminBootstrapRunner(
            ZipaiUserRepository users,
            ZipaiPasswordService passwords,
            @Value("${zipai.admin.username:}") String username,
            @Value("${zipai.admin.email:}") String email,
            @Value("${zipai.admin.phone:}") String phone,
            @Value("${zipai.admin.password:}") String password) {
        this.users = users;
        this.passwords = passwords;
        this.username = username.trim();
        this.email = email.trim().toLowerCase();
        this.phone = phone.replaceAll("[^0-9]", "");
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        validateConfiguration();

        var existing = users.findByUsernameIgnoreCase(username);
        if (existing.isPresent()) {
            ZipaiUser user = existing.get();
            if (!"admin".equals(user.getRole()) || !"active".equals(user.getStatus())) {
                throw new IllegalStateException(
                    "ZIPAI_ADMIN_USERNAME already exists but is not an active administrator.");
            }
            log.info("ZipAI administrator already exists; bootstrap skipped. username={}", username);
            return;
        }

        if (users.existsByEmailIgnoreCase(email)) {
            throw new IllegalStateException(
                "ZIPAI_ADMIN_EMAIL is already used by another account.");
        }

        LocalDateTime now = LocalDateTime.now();
        ZipaiUser admin = new ZipaiUser();
        admin.setUsername(username);
        admin.setEmail(email);
        admin.setPhone(phone);
        admin.setPasswordHash(passwords.encode(password));
        admin.setRole("admin");
        admin.setStatus("active");
        admin.setFailedLoginAttempts(0);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        users.save(admin);

        log.info("ZipAI administrator created. username={}", username);
    }

    private void validateConfiguration() {
        if (!username.matches("^[A-Za-z0-9_가-힣]{4,20}$")) {
            throw new IllegalStateException(
                "ZIPAI_ADMIN_USERNAME must be 4-20 characters using Korean, letters, numbers, or underscore.");
        }
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalStateException("ZIPAI_ADMIN_EMAIL is invalid.");
        }
        if (phone.length() < 10 || phone.length() > 11) {
            throw new IllegalStateException("ZIPAI_ADMIN_PHONE must contain 10-11 digits.");
        }
        if (password.length() < 12 || password.length() > 72
                || !password.matches(".*[A-Za-z].*")
                || !password.matches(".*[0-9].*")
                || !password.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalStateException(
                "ZIPAI_ADMIN_PASSWORD must be 12-72 characters and include a letter, number, and special character.");
        }
    }
}
