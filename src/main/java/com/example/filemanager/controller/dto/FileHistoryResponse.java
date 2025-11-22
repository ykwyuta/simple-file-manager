package com.example.filemanager.controller.dto;

import com.example.filemanager.domain.FileHistory;
import java.time.LocalDateTime;

public class FileHistoryResponse {

  private final Long id;
  private final int version;
  private final String modifier;
  private final LocalDateTime createdAt;

  public FileHistoryResponse(FileHistory history) {
    this.id = history.getId();
    this.version = history.getVersion();
    this.modifier = history.getModifier().getUsername(); // Avoid exposing full user object
    this.createdAt = history.getCreatedAt();
  }

  public Long getId() {
    return id;
  }

  public int getVersion() {
    return version;
  }

  public String getModifier() {
    return modifier;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
