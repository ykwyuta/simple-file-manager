package com.example.filemanager.controller.dto;

import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.User;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Read model for a user.
 *
 * <p>
 * Exists so the JPA entity is never serialized directly: {@code User} exposes
 * the bcrypt hash through its getter and holds a bidirectional link to
 * {@code Group}, which Jackson follows until it exhausts its depth limit.
 */
public class UserResponse {

    private final Long id;
    private final String username;
    private final List<GroupSummary> groups;

    public UserResponse(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.groups = user.getGroups().stream()
                .map(GroupSummary::new)
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .collect(Collectors.toList());
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public List<GroupSummary> getGroups() {
        return groups;
    }

    /** A group as seen from a user, without the members back-reference. */
    public static class GroupSummary {
        private final Long id;
        private final String name;

        public GroupSummary(Group group) {
            this.id = group.getId();
            this.name = group.getName();
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
