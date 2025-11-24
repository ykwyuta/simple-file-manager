package com.example.filemanager.controller.dto;

import jakarta.validation.constraints.NotNull;

public class TagsRequest {
    @NotNull
    private String tags;

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }
}
