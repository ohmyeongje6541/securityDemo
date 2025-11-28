package org.example.sequritydemo.config;

import lombok.RequiredArgsConstructor;
import org.example.sequritydemo.entity.User;
import org.example.sequritydemo.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        User user = User.builder()
            .username("user")
            .password(passwordEncoder.encode("1234"))
            .role("ROLE_USER")
            .build();

        User admin = User.builder()
            .username("admin")
            .password(passwordEncoder.encode("admin"))
            .role("ROLE_ADMIN")
            .build();

        userRepository.save(user);
        userRepository.save(admin);

        System.out.println("user 데이터 생성완료");
    }

}
