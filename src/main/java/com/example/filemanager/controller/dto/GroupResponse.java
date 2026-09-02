package com.example.filemanager.controller.dto;

import com.example.filemanager.domain.Group;

/** Read model for a group. Deliberately omits the member list. */
public class GroupResponse {

    private final Long id;
    private final String name;

    public GroupResponse(Group group) {
        this.id = group.getId();
        this.name = group.getName();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
