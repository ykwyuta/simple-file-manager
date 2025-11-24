package com.example.filemanager.controller;

import com.example.filemanager.controller.dto.FileResponse;
import com.example.filemanager.controller.dto.FolderRequest;
import com.example.filemanager.controller.dto.FileHistoryResponse;
import com.example.filemanager.controller.dto.MoveRequest;
import com.example.filemanager.controller.dto.LockRequest;
import com.example.filemanager.controller.dto.RenameRequest;
import com.example.filemanager.controller.dto.VersioningRequest;
import com.example.filemanager.controller.dto.ChangeOwnerRequest;
import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.FileHistory;
import com.example.filemanager.service.FileService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.util.Objects;

@RestController
@RequestMapping("/api/files")
public class FileController {

  private final FileService fileService;

  public FileController(FileService fileService) {
    this.fileService = fileService;
  }

  @PostMapping("/folders")
  public ResponseEntity<FileResponse> createFolder(@Valid @RequestBody FolderRequest request) {
    FileEntity newDirectory = fileService.createDirectory(Objects.requireNonNull(request));

    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(newDirectory.getId())
        .toUri();

    return ResponseEntity.created(location).body(new FileResponse(newDirectory));
  }

  @GetMapping
  public ResponseEntity<List<FileResponse>> listFiles(
      @RequestParam(value = "parentId", required = false) Long parentId) {
    List<FileEntity> files = fileService.listFiles(parentId);
    List<FileResponse> response = files.stream().map(FileResponse::new).collect(Collectors.toList());
    return ResponseEntity.ok(response);
  }

  @PostMapping
  public ResponseEntity<FileResponse> uploadFile(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "parentFolderId", required = false) Long parentFolderId,
      @RequestParam(value = "permissions", defaultValue = "644") String permissions)
      throws IOException {

    FileEntity newFile = fileService.uploadFile(Objects.requireNonNull(file), parentFolderId,
        Objects.requireNonNull(permissions));

    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(newFile.getId())
        .toUri();

    return ResponseEntity.created(location).body(new FileResponse(newFile));
  }

  @PutMapping("/{id}")
  public ResponseEntity<FileResponse> updateFile(
      @PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
    FileEntity updatedFile = fileService.updateFile(Objects.requireNonNull(id), Objects.requireNonNull(file));
    return ResponseEntity.ok(new FileResponse(updatedFile));
  }

  @GetMapping("/{id}")
  public ResponseEntity<Resource> downloadFile(@PathVariable Long id) throws IOException {
    FileEntity fileEntity = fileService.findFileById(Objects.requireNonNull(id));
    byte[] data = Objects.requireNonNull(fileService.downloadFile(Objects.requireNonNull(fileEntity)));
    ByteArrayResource resource = new ByteArrayResource(data);

    String encodedFilename = URLEncoder.encode(fileEntity.getName(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");

    return ResponseEntity.ok()
        .contentType(Objects.requireNonNull(MediaType.APPLICATION_OCTET_STREAM))
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
        .body(resource);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteFile(@PathVariable Long id) {
    fileService.softDeleteFile(Objects.requireNonNull(id));
  }

  @PutMapping("/{id}/name")
  public ResponseEntity<FileResponse> renameFile(
      @PathVariable Long id, @Valid @RequestBody RenameRequest request) {
    FileEntity updatedFile = fileService.renameFile(Objects.requireNonNull(id),
        Objects.requireNonNull(request.getNewName()));
    return ResponseEntity.ok(new FileResponse(updatedFile));
  }

  @PutMapping("/{id}/lock")
  public ResponseEntity<Void> updateLockStatus(@PathVariable Long id, @RequestBody LockRequest lockRequest,
      @AuthenticationPrincipal UserDetails userDetails) {
    fileService.updateLockStatus(Objects.requireNonNull(id), lockRequest.isLocked(),
        Objects.requireNonNull(userDetails.getUsername()));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/trash")
  public ResponseEntity<List<FileResponse>> getTrash() {
    List<FileEntity> deletedFiles = fileService.listDeletedFiles();
    List<FileResponse> response = deletedFiles.stream().map(FileResponse::new).collect(Collectors.toList());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/restore")
  public ResponseEntity<FileResponse> restoreFile(@PathVariable Long id) {
    FileEntity restoredFile = fileService.restoreFile(Objects.requireNonNull(id));
    return ResponseEntity.ok(new FileResponse(restoredFile));
  }

  @GetMapping("/search")
  public ResponseEntity<List<FileResponse>> searchFiles(
      @RequestParam(value = "name", required = false) String name,
      @RequestParam(value = "tags", required = false) String tags) {
    List<FileEntity> files = fileService.searchFiles(name, tags);
    List<FileResponse> response = files.stream().map(FileResponse::new).collect(Collectors.toList());
    return ResponseEntity.ok(response);
  }

  @PutMapping("/{id}/parent")
  public ResponseEntity<FileResponse> moveFile(
      @PathVariable Long id, @Valid @RequestBody MoveRequest request) {
    FileEntity movedFile = fileService.moveFile(Objects.requireNonNull(id),
        Objects.requireNonNull(request.getNewParentId()));
    return ResponseEntity.ok(new FileResponse(movedFile));
  }

  @PutMapping("/folders/{id}/versioning")
  public ResponseEntity<FileResponse> toggleVersioning(
      @PathVariable Long id, @Valid @RequestBody VersioningRequest request) {
    FileEntity updatedFolder = fileService.toggleVersioning(Objects.requireNonNull(id), request.getEnabled());
    return ResponseEntity.ok(new FileResponse(updatedFolder));
  }

  @GetMapping("/{id}/versions")
  public ResponseEntity<List<FileHistoryResponse>> getFileVersions(@PathVariable Long id) {
    List<FileHistory> versions = fileService.getFileVersions(Objects.requireNonNull(id));
    List<FileHistoryResponse> response = versions.stream().map(FileHistoryResponse::new).collect(Collectors.toList());
    return ResponseEntity.ok(response);
  }

  @PostMapping("/{id}/restore/{versionId}")
  public ResponseEntity<FileResponse> restoreFileVersion(
      @PathVariable Long id, @PathVariable Long versionId) {
    FileEntity restoredFile = fileService.restoreFileVersion(Objects.requireNonNull(id),
        Objects.requireNonNull(versionId));
    return ResponseEntity.ok(new FileResponse(restoredFile));
  }

  @PutMapping("/{id}/owner")
  public ResponseEntity<FileResponse> changeOwner(
      @PathVariable Long id, @Valid @RequestBody ChangeOwnerRequest request) {
    FileEntity updatedFile = fileService.changeOwner(Objects.requireNonNull(id),
        Objects.requireNonNull(request.getOwnerUserId()), Objects.requireNonNull(request.getOwnerGroupId()),
        request.isRecursive());
    return ResponseEntity.ok(new FileResponse(updatedFile));
  }

  @PutMapping("/{id}/tags")
  public ResponseEntity<FileResponse> updateTags(
      @PathVariable Long id, @Valid @RequestBody com.example.filemanager.controller.dto.TagsRequest request) {
    FileEntity updatedFile = fileService.updateTags(Objects.requireNonNull(id),
        Objects.requireNonNull(request.getTags()));
    return ResponseEntity.ok(new FileResponse(updatedFile));
  }
}
