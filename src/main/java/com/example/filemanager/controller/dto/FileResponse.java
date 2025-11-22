package com.example.filemanager.controller.dto;

import com.example.filemanager.domain.FileEntity;
import java.time.LocalDateTime;

public class FileResponse {

    private Long id;
    private String name;
    private boolean isDirectory;
    private Long parentFolderId;
    private Integer permissions;
    private Long ownerId;
    private Long groupId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public FileResponse(FileEntity entity) {
        this.id = entity.getId();
        this.name = entity.getName();
        this.isDirectory = entity.isDirectory();
        if (entity.getParent() != null) {
            this.parentFolderId = entity.getParent().getId();
        }
        this.permissions = entity.getPermissions();
        this.ownerId = entity.getOwner().getId();
        this.groupId = entity.getGroup().getId();
        this.createdAt = entity.getCreatedAt();
        this.updatedAt = entity.getUpdatedAt();
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public Long getParentFolderId() {
        return parentFolderId;
    }

    public Integer getPermissions() {
        return permissions;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
