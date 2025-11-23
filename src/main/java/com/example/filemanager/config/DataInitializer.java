package com.example.filemanager.config;

import com.example.filemanager.config.CsvDataLoader.UserData;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.User;
import com.example.filemanager.repository.GroupRepository;
import com.example.filemanager.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository,
            GroupRepository groupRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            // Try to load from CSV files first
            boolean loadedFromCsv = loadFromCsvFiles(userRepository, groupRepository, passwordEncoder);

            if (!loadedFromCsv) {
                // Fallback to hardcoded values
                loadHardcodedData(userRepository, groupRepository, passwordEncoder);
            }
        };
    }

    private boolean loadFromCsvFiles(UserRepository userRepository,
            GroupRepository groupRepository,
            PasswordEncoder passwordEncoder) {
        try {
            // Check if CSV files exist
            ClassPathResource groupCsv = new ClassPathResource("group.csv");
            ClassPathResource userCsv = new ClassPathResource("user.csv");

            if (!groupCsv.exists() || !userCsv.exists()) {
                System.out.println("===========================================");
                System.out.println("CSV files not found. Using hardcoded data.");
                System.out.println("===========================================");
                return false;
            }

            System.out.println("===========================================");
            System.out.println("Loading users and groups from CSV files...");
            System.out.println("===========================================");

            // Load groups first
            Map<String, Group> groupMap = new HashMap<>();
            try (InputStream groupStream = groupCsv.getInputStream()) {
                List<String> groupNames = CsvDataLoader.loadGroups(groupStream);
                for (String groupName : groupNames) {
                    if (groupRepository.findByName(groupName).isEmpty()) {
                        Group group = new Group();
                        group.setName(groupName);
                        group = groupRepository.save(group);
                        groupMap.put(groupName, group);
                        System.out.println("Created group: " + groupName);
                    } else {
                        groupMap.put(groupName, groupRepository.findByName(groupName).get());
                        System.out.println("Group already exists: " + groupName);
                    }
                }
            }

            // Load users
            try (InputStream userStream = userCsv.getInputStream()) {
                List<UserData> users = CsvDataLoader.loadUsers(userStream);
                for (UserData userData : users) {
                    if (userRepository.findByUsername(userData.getUsername()).isEmpty()) {
                        User user = new User();
                        user.setUsername(userData.getUsername());
                        user.setPassword(passwordEncoder.encode(userData.getPassword()));

                        // Assign groups
                        Set<Group> userGroups = new HashSet<>();
                        for (String groupName : userData.getGroupNames()) {
                            Group group = groupMap.get(groupName);
                            if (group == null) {
                                group = groupRepository.findByName(groupName)
                                        .orElseThrow(() -> new IllegalArgumentException(
                                                "Group not found: " + groupName));
                            }
                            userGroups.add(group);
                        }
                        user.setGroups(userGroups);

                        userRepository.save(user);
                        System.out.println("Created user: " + userData.getUsername() +
                                " with groups: " + userData.getGroupNames());
                    } else {
                        System.out.println("User already exists: " + userData.getUsername());
                    }
                }
            }

            System.out.println("===========================================");
            System.out.println("CSV data loading completed successfully!");
            System.out.println("===========================================");
            return true;

        } catch (Exception e) {
            System.err.println("===========================================");
            System.err.println("Error loading CSV files: " + e.getMessage());
            System.err.println("Falling back to hardcoded data...");
            System.err.println("===========================================");
            e.printStackTrace();
            return false;
        }
    }

    private void loadHardcodedData(UserRepository userRepository,
            GroupRepository groupRepository,
            PasswordEncoder passwordEncoder) {
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
    }
}
