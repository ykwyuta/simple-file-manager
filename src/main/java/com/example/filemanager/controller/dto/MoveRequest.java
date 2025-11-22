package com.example.filemanager.controller.dto;

import jakarta.validation.constraints.NotNull;

public class MoveRequest {

  @NotNull(message = "New parent folder ID cannot be null")
  private Long newParentId;

  public Long getNewParentId() {
    return newParentId;
  }

  public void setNewParentId(Long newParentId) {
    this.newParentId = newParentId;
  }
}
