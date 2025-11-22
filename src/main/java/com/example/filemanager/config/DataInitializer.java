package com.example.filemanager.config;

import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.User;
import com.example.filemanager.repository.GroupRepository;
import com.example.filemanager.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository,
            GroupRepository groupRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            // Check if admin user already exists
            if (userRepository.findByUsername("admin").isEmpty()) {
                // Create or get admin group
                Group adminGroup = groupRepository.findByName("admins")
                        .orElseGet(() -> {
                            Group g = new Group();
                            g.setName("admins");
                            return groupRepository.save(g);
                        });

                // Create admin user
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin"));
                admin.setGroups(Set.of(adminGroup));
                userRepository.save(admin);

                System.out.println("===========================================");
                System.out.println("Initial admin user created:");
                System.out.println("  Username: admin");
                System.out.println("  Password: admin");
                System.out.println("===========================================");
            }

            // Create demo user if not exists
            if (userRepository.findByUsername("user").isEmpty()) {
                // Create or get users group
                Group userGroup = groupRepository.findByName("users")
                        .orElseGet(() -> {
                            Group g = new Group();
                            g.setName("users");
                            return groupRepository.save(g);
                        });

                // Create demo user
                User demoUser = new User();
                demoUser.setUsername("user");
                demoUser.setPassword(passwordEncoder.encode("password"));
                demoUser.setGroups(Set.of(userGroup));
                userRepository.save(demoUser);

                System.out.println("===========================================");
                System.out.println("Demo user created:");
                System.out.println("  Username: user");
                System.out.println("  Password: password");
                System.out.println("===========================================");
            }
        };
    }
}
