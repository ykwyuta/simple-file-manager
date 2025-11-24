package com.example.filemanager.controller;

import com.example.filemanager.domain.User;
import com.example.filemanager.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import java.util.Objects;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        User createdUser = userService.createUser(Objects.requireNonNull(user));
        return ResponseEntity.created(Objects.requireNonNull(java.net.URI.create("/api/users/" + createdUser.getId())))
                .body(createdUser);
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return userService.findUserById(Objects.requireNonNull(id))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userService.findAllUsers();
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User userDetails) {
        return ResponseEntity.ok(userService.updateUser(Objects.requireNonNull(id), userDetails));
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
