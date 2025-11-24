package com.example.filemanager.controller.dto;

import jakarta.validation.constraints.NotNull;

public class ChangeOwnerRequest {

    @NotNull
    private Long ownerUserId;

    @NotNull
    private Long ownerGroupId;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getOwnerGroupId() {
        return ownerGroupId;
    }

    public void setOwnerGroupId(Long ownerGroupId) {
        this.ownerGroupId = ownerGroupId;
    }

    private boolean recursive;

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }
}
