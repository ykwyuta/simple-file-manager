package com.example.filemanager.controller;

import com.example.filemanager.domain.Group;
import com.example.filemanager.service.GroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import java.util.Objects;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    public ResponseEntity<Group> createGroup(@RequestBody Group group) {
        Group createdGroup = groupService.createGroup(Objects.requireNonNull(group));
        return ResponseEntity
                .created(Objects.requireNonNull(java.net.URI.create("/api/groups/" + createdGroup.getId())))
                .body(createdGroup);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Group> getGroupById(@PathVariable Long id) {
        return groupService.findGroupById(Objects.requireNonNull(id))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<Group> getAllGroups() {
        return groupService.findAllGroups();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Group> updateGroup(@PathVariable Long id, @RequestBody Group groupDetails) {
        return ResponseEntity.ok(groupService.updateGroup(Objects.requireNonNull(id), groupDetails));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        groupService.deleteGroup(Objects.requireNonNull(id));
        return ResponseEntity.noContent().build();
    }
}
