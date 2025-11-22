package com.example.filemanager.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CsvDataLoader {

    public static class UserData {
        private final String username;
        private final String password;
        private final List<String> groupNames;

        public UserData(String username, String password, List<String> groupNames) {
            this.username = username;
            this.password = password;
            this.groupNames = groupNames;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public List<String> getGroupNames() {
            return groupNames;
        }
    }

    /**
     * Load group names from CSV file.
     * Expected format: name
     * 
     * @param inputStream CSV file input stream
     * @return List of group names
     */
    public static List<String> loadGroups(InputStream inputStream) throws IOException {
        List<String> groups = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            // Skip header line
            String header = reader.readLine();
            if (header == null || !header.trim().equalsIgnoreCase("name")) {
                throw new IllegalArgumentException("Invalid group CSV format. Expected header: name");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                String groupName = line.trim();
                if (!groupName.isEmpty()) {
                    groups.add(groupName);
                }
            }
        }

        return groups;
    }

    /**
     * Load user data from CSV file.
     * Expected format: username,password,groups
     * Groups can be comma-separated in quotes: "group1,group2"
     * 
     * @param inputStream CSV file input stream
     * @return List of UserData objects
     */
    public static List<UserData> loadUsers(InputStream inputStream) throws IOException {
        List<UserData> users = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            // Skip header line
            String header = reader.readLine();
            if (header == null || !header.trim().equalsIgnoreCase("username,password,groups")) {
                throw new IllegalArgumentException(
                        "Invalid user CSV format. Expected header: username,password,groups");
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                try {
                    UserData userData = parseCsvLine(line);
                    users.add(userData);
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "Error parsing user CSV at line " + lineNumber + ": " + e.getMessage(), e);
                }
            }
        }

        return users;
    }

    private static UserData parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(currentField.toString().trim());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        fields.add(currentField.toString().trim());

        if (fields.size() != 3) {
            throw new IllegalArgumentException(
                    "Expected 3 fields (username,password,groups) but got " + fields.size());
        }

        String username = fields.get(0);
        String password = fields.get(1);
        String groupsStr = fields.get(2);

        // Parse groups (can be comma-separated)
        List<String> groupNames = Arrays.stream(groupsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        return new UserData(username, password, groupNames);
    }
}
