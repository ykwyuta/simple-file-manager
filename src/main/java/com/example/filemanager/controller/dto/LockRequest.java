package com.example.filemanager.controller.dto;

public class LockRequest {
    private boolean locked;

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
