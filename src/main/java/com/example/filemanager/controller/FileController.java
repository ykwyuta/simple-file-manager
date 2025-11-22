package com.example.filemanager.controller;

import com.example.filemanager.controller.dto.FileResponse;
import com.example.filemanager.controller.dto.FolderRequest;
import com.example.filemanager.domain.FileEntity;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/files")
public class FileController {

  private final FileService fileService;

  public FileController(FileService fileService) {
    this.fileService = fileService;
  }

  @PostMapping("/folders")
  public ResponseEntity<FileResponse> createFolder(@Valid @RequestBody FolderRequest request) {
    FileEntity newDirectory = fileService.createDirectory(request);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(newDirectory.getId())
            .toUri();

    return ResponseEntity.created(location).body(new FileResponse(newDirectory));
  }

  @PostMapping
  public ResponseEntity<FileResponse> uploadFile(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "parentFolderId", required = false) Long parentFolderId,
      @RequestParam(value = "permissions", defaultValue = "644") String permissions)
      throws IOException {

    FileEntity newFile = fileService.uploadFile(file, parentFolderId, permissions);

    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(newFile.getId())
            .toUri();

    return ResponseEntity.created(location).body(new FileResponse(newFile));
  }

  @GetMapping("/{id}")
  public ResponseEntity<Resource> downloadFile(@PathVariable Long id) throws IOException {
    FileEntity fileEntity = fileService.findFileById(id);
    byte[] data = fileService.downloadFile(fileEntity);
    ByteArrayResource resource = new ByteArrayResource(data);

    String encodedFilename =
        URLEncoder.encode(fileEntity.getName(), StandardCharsets.UTF_8).replaceAll("\\+", "%20");

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFilename)
        .body(resource);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteFile(@PathVariable Long id) {
    fileService.softDeleteFile(id);
  }

  @GetMapping("/search")
  public ResponseEntity<List<FileResponse>> searchFiles(
      @RequestParam(value = "name", required = false) String name,
      @RequestParam(value = "tags", required = false) String tags) {
    List<FileEntity> files = fileService.searchFiles(name, tags);
    List<FileResponse> response =
        files.stream().map(FileResponse::new).collect(Collectors.toList());
    return ResponseEntity.ok(response);
  }
}
