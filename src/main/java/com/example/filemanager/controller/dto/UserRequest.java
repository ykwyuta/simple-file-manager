package com.example.filemanager.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Write model for creating or updating a user. */
public class UserRequest {

    @NotBlank(message = "ユーザー名を入力してください。")
    @Size(max = 64, message = "ユーザー名は64文字以内で入力してください。")
    @Pattern(regexp = "[A-Za-z0-9._@-]+", message = "ユーザー名に使用できるのは英数字と . _ @ - のみです。")
    private String username;

    /** Optional on update: blank means "keep the current password". */
    @Size(max = 128, message = "パスワードは128文字以内で入力してください。")
    private String password;

    private List<Long> groupIds;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<Long> getGroupIds() {
        return groupIds;
    }

    public void setGroupIds(List<Long> groupIds) {
        this.groupIds = groupIds;
    }
}
