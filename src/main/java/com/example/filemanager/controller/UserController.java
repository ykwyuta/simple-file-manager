package com.example.filemanager.controller;

import com.example.filemanager.controller.dto.UserRequest;
import com.example.filemanager.controller.dto.UserResponse;
import com.example.filemanager.domain.User;
import com.example.filemanager.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Administrator-only user management.
 *
 * <p>
 * Access is enforced in {@code SecurityConfig} at the URL level and again here
 * via method security, so moving or renaming the mapping cannot silently open
 * it up. Without that, any authenticated user could add themselves to the
 * administrator group through {@code POST /{userId}/groups/{groupId}}.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
        User createdUser = userService.createUser(Objects.requireNonNull(request));
        return ResponseEntity
                .created(URI.create("/api/users/" + createdUser.getId()))
                .body(new UserResponse(createdUser));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return userService.findUserById(Objects.requireNonNull(id))
                .map(UserResponse::new)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<UserResponse> getAllUsers() {
        return userService.findAllUsers().stream().map(UserResponse::new).collect(Collectors.toList());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(new UserResponse(userService.updateUser(Objects.requireNonNull(id), request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(Objects.requireNonNull(id));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/groups/{groupId}")
    public ResponseEntity<Void> addUserToGroup(@PathVariable Long userId, @PathVariable Long groupId) {
        userService.addUserToGroup(Objects.requireNonNull(userId), Objects.requireNonNull(groupId));
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}/groups/{groupId}")
    public ResponseEntity<Void> removeUserFromGroup(@PathVariable Long userId, @PathVariable Long groupId) {
        userService.removeUserFromGroup(Objects.requireNonNull(userId), Objects.requireNonNull(groupId));
        return ResponseEntity.noContent().build();
    }
}
