package com.agrolink.app.config;

import com.agrolink.app.model.Category;
import com.agrolink.app.model.Role;
import com.agrolink.app.model.User;
import com.agrolink.app.repository.CategoryRepository;
import com.agrolink.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        ensureSuperAdmin();
        seedCategories();
    }

    private void ensureSuperAdmin() {
        if (userRepository.findByEmail("superadmin@agrolink.com").isPresent()) {
            log.info("DataInitializer - super admin already present");
            return;
        }
        seedUser("Super Admin", "superadmin@agrolink.com", "admin123", Role.SUPER_ADMIN);
        log.info("DataInitializer - super admin created");
    }

    private void seedCategories() {
        if (categoryRepository.count() > 0) {
            log.info("DataInitializer - categories already present");
            return;
        }
        categoryRepository.saveAll(List.of(
                Category.builder().name("Grains").description("Rice, wheat, millets and more")
                        .icon("crop").displayOrder(1).build(),
                Category.builder().name("Vegetables").description("Fresh seasonal vegetables")
                        .icon("vegetable").displayOrder(2).build(),
                Category.builder().name("Fruits").description("Farm-fresh fruits")
                        .icon("fruit").displayOrder(3).build(),
                Category.builder().name("Dairy").description("Milk, cheese, paneer and dairy products")
                        .icon("dairy").displayOrder(4).build()
        ));
        log.info("DataInitializer - categories seeded");
    }

    private User seedUser(String name, String email, String rawPassword, Role role) {
        User user = User.builder()
                .name(name)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .role(role)
                .isActive(true)
                .createdAt(Instant.now().minus(20, ChronoUnit.DAYS))
                .build();
        return userRepository.save(user);
    }
}