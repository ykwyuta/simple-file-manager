package com.example.filemanager.config;

import com.example.filemanager.config.CsvDataLoader.UserData;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.User;
import com.example.filemanager.repository.GroupRepository;
import com.example.filemanager.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private static final String BOOTSTRAP_ADMIN_USERNAME = "admin";
    private static final String DEMO_USERNAME = "user";

    /**
     * Password for the bootstrap administrator, created only when no admin
     * account exists yet. Override with {@code APP_BOOTSTRAP_ADMIN_PASSWORD}.
     */
    @Value("${app.bootstrap.admin-password:admin}")
    private String bootstrapAdminPassword;

    /** Whether to create the demo "user" account. Off outside development. */
    @Value("${app.bootstrap.demo-user:false}")
    private boolean createDemoUser;

    @Value("${app.bootstrap.demo-user-password:password}")
    private String demoUserPassword;

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
                logger.info("No group.csv/user.csv on the classpath; using the built-in bootstrap accounts.");
                return false;
            }

            logger.info("Loading users and groups from CSV files");

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
                        logger.info("Created group '{}'", groupName);
                    } else {
                        groupMap.put(groupName, groupRepository.findByName(groupName).get());
                        logger.debug("Group '{}' already exists", groupName);
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
                        logger.info("Created user '{}' in groups {}", userData.getUsername(),
                                userData.getGroupNames());
                    } else {
                        logger.debug("User '{}' already exists", userData.getUsername());
                    }
                }
            }

            logger.info("CSV bootstrap data loaded");
            return true;

        } catch (Exception e) {
            logger.error("Error loading CSV bootstrap files: {}", e.getMessage());
            logger.error("Falling back to the built-in bootstrap accounts", e);
            return false;
        }
    }

    private void loadHardcodedData(UserRepository userRepository,
            GroupRepository groupRepository,
            PasswordEncoder passwordEncoder) {
        // Check if admin user already exists
        if (userRepository.findByUsername(BOOTSTRAP_ADMIN_USERNAME).isEmpty()) {
            // Create or get admin group
            Group adminGroup = groupRepository.findByName("admins")
                    .orElseGet(() -> {
                        Group g = new Group();
                        g.setName("admins");
                        return groupRepository.save(g);
                    });

            // Create admin user
            User admin = new User();
            admin.setUsername(BOOTSTRAP_ADMIN_USERNAME);
            admin.setPassword(passwordEncoder.encode(bootstrapAdminPassword));
            admin.setGroups(Set.of(adminGroup));
            userRepository.save(admin);

            // The password is intentionally not logged. It is the documented
            // bootstrap default (see docs/security.md) and must be changed at
            // first login; writing it to the log would persist it in plain text.
            if ("admin".equals(bootstrapAdminPassword)) {
                logger.warn("Bootstrap administrator '{}' created with the DEFAULT password. "
                        + "Set app.bootstrap.admin-password (APP_BOOTSTRAP_ADMIN_PASSWORD) "
                        + "or change it at first login before exposing this instance.",
                        BOOTSTRAP_ADMIN_USERNAME);
            } else {
                logger.info("Bootstrap administrator '{}' created.", BOOTSTRAP_ADMIN_USERNAME);
            }
        }

        // Create demo user if not exists
        if (createDemoUser && userRepository.findByUsername(DEMO_USERNAME).isEmpty()) {
            // Create or get users group
            Group userGroup = groupRepository.findByName("users")
                    .orElseGet(() -> {
                        Group g = new Group();
                        g.setName("users");
                        return groupRepository.save(g);
                    });

            // Create demo user
            User demoUser = new User();
            demoUser.setUsername(DEMO_USERNAME);
            demoUser.setPassword(passwordEncoder.encode(demoUserPassword));
            demoUser.setGroups(Set.of(userGroup));
            userRepository.save(demoUser);

            logger.warn("Demo user '{}' created. Disable app.bootstrap.demo-user "
                    + "before exposing this instance.", DEMO_USERNAME);
        }
    }
}
