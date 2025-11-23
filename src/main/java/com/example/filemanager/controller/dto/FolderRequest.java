package com.example.filemanager.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class FolderRequest {

    @NotBlank(message = "Folder name cannot be blank")
    private String name;

    private Long parentFolderId; // Nullable for root folder

    @NotNull(message = "Permissions cannot be null")
    @Pattern(regexp = "^[0-7]{3}$", message = "Permissions must be a 3-digit number with each digit 0-7 (e.g., 755)")
    private String permissions;

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getParentFolderId() {
        return parentFolderId;
    }

    public void setParentFolderId(Long parentFolderId) {
        this.parentFolderId = parentFolderId;
    }

    public String getPermissions() {
        return permissions;
    }

    public void setPermissions(String permissions) {
        this.permissions = permissions;
    }
}
