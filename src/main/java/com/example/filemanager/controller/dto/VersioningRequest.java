package com.example.filemanager.controller.dto;

import jakarta.validation.constraints.NotNull;

public class VersioningRequest {

  @NotNull(message = "The 'enabled' field must not be null.")
  private Boolean enabled;

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }
}
