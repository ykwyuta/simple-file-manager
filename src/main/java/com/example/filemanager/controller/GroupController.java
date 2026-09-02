package com.example.filemanager.controller;

import com.example.filemanager.controller.dto.GroupRequest;
import com.example.filemanager.controller.dto.GroupResponse;
import com.example.filemanager.domain.Group;
import com.example.filemanager.service.GroupService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Administrator-only group management. See {@link UserController}. */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody GroupRequest request) {
        Group createdGroup = groupService.createGroup(Objects.requireNonNull(request));
        return ResponseEntity
                .created(URI.create("/api/groups/" + createdGroup.getId()))
                .body(new GroupResponse(createdGroup));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupResponse> getGroupById(@PathVariable Long id) {
        return groupService.findGroupById(Objects.requireNonNull(id))
                .map(GroupResponse::new)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<GroupResponse> getAllGroups() {
        return groupService.findAllGroups().stream().map(GroupResponse::new).collect(Collectors.toList());
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupResponse> updateGroup(@PathVariable Long id, @Valid @RequestBody GroupRequest request) {
        return ResponseEntity.ok(new GroupResponse(groupService.updateGroup(Objects.requireNonNull(id), request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        groupService.deleteGroup(Objects.requireNonNull(id));
        return ResponseEntity.noContent().build();
    }
}
