package com.example.filemanager.service;

import com.example.filemanager.controller.dto.FolderRequest;
import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.User;
import com.example.filemanager.exception.DuplicateFileException;
import com.example.filemanager.exception.InvalidPermissionFormatException;
import com.example.filemanager.exception.ParentNotDirectoryException;
import com.example.filemanager.exception.ResourceNotFoundException;
import com.example.filemanager.repository.FileRepository;
import com.example.filemanager.repository.FileSpecification;
import io.awspring.cloud.s3.S3Template;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {

  private final FileRepository fileRepository;
  private final S3Template s3Template;
  private final PermissionService permissionService;

  @Value("${S3_BUCKET_NAME}")
  private String bucketName;

  public FileService(
      FileRepository fileRepository, S3Template s3Template, PermissionService permissionService) {
    this.fileRepository = fileRepository;
    this.s3Template = s3Template;
    this.permissionService = permissionService;
  }

  @Transactional
  public FileEntity uploadFile(MultipartFile file, Long parentFolderId, String permissions)
      throws IOException {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

    FileEntity parent = null;
    if (parentFolderId != null) {
      parent =
          fileRepository
              .findByIdAndDeletedAtIsNull(parentFolderId)
              .orElseThrow(
                  () -> new ResourceNotFoundException("Parent folder not found with id: " + parentFolderId));
      if (!parent.isDirectory()) {
        throw new ParentNotDirectoryException(
            "Parent with id " + parentFolderId + " is not a directory.");
      }
    }

    fileRepository
        .findByParentAndNameAndDeletedAtIsNull(parent, file.getOriginalFilename())
        .ifPresent(
            f -> {
              throw new DuplicateFileException(
                  "A file or directory with the name '"
                      + file.getOriginalFilename()
                      + "' already exists in this location.");
            });

    Group group =
        currentUser
            .getGroups()
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("User does not belong to any group."));

    FileEntity newFile = new FileEntity();
    newFile.setName(file.getOriginalFilename());
    newFile.setDirectory(false);
    newFile.setParent(parent);
    newFile.setOwner(currentUser);
    newFile.setGroup(group);
    try {
      newFile.setPermissions(Integer.parseInt(permissions, 8)); // Parse octal string
    } catch (NumberFormatException e) {
      throw new InvalidPermissionFormatException(
          "Invalid permission format. Please use an octal number string (e.g., '755').");
    }

    String s3Key = UUID.randomUUID() + "/" + file.getOriginalFilename();
    s3Template.upload(bucketName, s3Key, file.getInputStream());
    newFile.setStorageKey(s3Key);

    return fileRepository.save(newFile);
  }

  public byte[] downloadFile(FileEntity fileEntity) throws IOException {
    if (fileEntity.isDirectory()) {
      throw new IllegalArgumentException("Cannot download a directory.");
    }
    if (fileEntity.getStorageKey() == null) {
      // This case should ideally not happen for a file, but as a safeguard:
      throw new IllegalStateException("File entity is missing storage key.");
    }
    return s3Template.download(bucketName, fileEntity.getStorageKey()).getInputStream().readAllBytes();
  }

  @Transactional(readOnly = true)
  public FileEntity findFileById(Long fileId) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    FileEntity fileEntity =
        fileRepository
            .findByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

    if (!permissionService.canRead(fileEntity, currentUser)) {
      throw new AccessDeniedException("You do not have permission to access this file.");
    }

    return fileEntity;
  }

  @Transactional
  public FileEntity createDirectory(FolderRequest request) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

    FileEntity parent = null;
    if (request.getParentFolderId() != null) {
      parent =
          fileRepository
              .findByIdAndDeletedAtIsNull(request.getParentFolderId())
              .orElseThrow(
                  () ->
                      new ResourceNotFoundException(
                          "Parent folder not found with id: " + request.getParentFolderId()));
      if (!parent.isDirectory()) {
        throw new ParentNotDirectoryException(
            "Parent with id " + request.getParentFolderId() + " is not a directory.");
      }
    }

    fileRepository
        .findByParentAndNameAndDeletedAtIsNull(parent, request.getName())
        .ifPresent(
            f -> {
              throw new DuplicateFileException(
                  "A file or directory with the name '"
                      + request.getName()
                      + "' already exists in this location.");
            });

    // The primary group of the user is used as the folder's group.
    // A more sophisticated implementation might allow selecting a group.
    Group group =
        currentUser
            .getGroups()
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("User does not belong to any group."));

    FileEntity newDirectory = new FileEntity();
    newDirectory.setName(request.getName());
    newDirectory.setDirectory(true);
    newDirectory.setParent(parent);
    newDirectory.setOwner(currentUser);
    newDirectory.setGroup(group);
    newDirectory.setPermissions(Integer.parseInt(request.getPermissions(), 8)); // Parse octal string

    return fileRepository.save(newDirectory);
  }

  @Transactional
  public void softDeleteFile(Long fileId) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    FileEntity fileEntity =
        fileRepository
            .findByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

    if (!permissionService.canWrite(fileEntity, currentUser)) {
      throw new AccessDeniedException("You do not have permission to delete this file.");
    }

    fileEntity.setDeletedAt(LocalDateTime.now());
    fileRepository.save(fileEntity);
  }

  @Transactional
  public FileEntity renameFile(Long fileId, String newName) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    FileEntity fileEntity =
        fileRepository
            .findByIdAndDeletedAtIsNull(fileId)
            .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

    if (!permissionService.canWrite(fileEntity, currentUser)) {
      throw new AccessDeniedException("You do not have permission to rename this file.");
    }

    fileRepository
        .findByParentAndNameAndDeletedAtIsNull(fileEntity.getParent(), newName)
        .ifPresent(
            f -> {
              throw new DuplicateFileException(
                  "A file or directory with the name '"
                      + newName
                      + "' already exists in this location.");
            });

    fileEntity.setName(newName);
    return fileRepository.save(fileEntity);
  }

  @Transactional(readOnly = true)
  public List<FileEntity> searchFiles(String name, String tags) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    Specification<FileEntity> spec = Specification.where(FileSpecification.isNotDeleted());

    if (StringUtils.hasText(name)) {
      spec = spec.and(FileSpecification.nameContains(name));
    }
    if (StringUtils.hasText(tags)) {
      spec = spec.and(FileSpecification.tagsContain(tags));
    }

    List<FileEntity> allFiles = fileRepository.findAll(spec);

    return allFiles.stream()
        .filter(file -> permissionService.canRead(file, currentUser))
        .collect(Collectors.toList());
  }
}
