package com.ecommerce.config;

import com.ecommerce.domain.Role;
import com.ecommerce.domain.User;
import com.ecommerce.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor

public class DataSeeder implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.existsByEmail("anthony.viveros@admin.com")){
            return;
        }

        User admin = User.builder()
                .email("anthony.viveros@admin.com")
                .password(passwordEncoder.encode("tu-password-admin"))
                .firstName("Anthony")
                .lastName("Viveros")
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);

    }




}
