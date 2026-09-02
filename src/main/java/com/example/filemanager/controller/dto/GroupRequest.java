package com.example.filemanager.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Write model for creating or updating a group. */
public class GroupRequest {

    @NotBlank(message = "グループ名を入力してください。")
    @Size(max = 64, message = "グループ名は64文字以内で入力してください。")
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "グループ名に使用できるのは英数字と . _ - のみです。")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
