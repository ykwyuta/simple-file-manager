package com.example.filemanager.controller.dto;

import jakarta.validation.constraints.NotBlank;

public class RenameRequest {

    @NotBlank(message = "New name cannot be blank")
    private String newName;

    public String getNewName() {
        return newName;
    }

    public void setNewName(String newName) {
        this.newName = newName;
    }
}
