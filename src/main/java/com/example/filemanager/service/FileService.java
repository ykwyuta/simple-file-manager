package com.example.filemanager.service;

import com.example.filemanager.controller.dto.FolderRequest;
import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.User;
import com.example.filemanager.exception.DuplicateFileException;
import com.example.filemanager.exception.InvalidPermissionFormatException;
import com.example.filemanager.domain.FileHistory;
import com.example.filemanager.exception.ParentNotDirectoryException;
import com.example.filemanager.exception.FileLockedException;
import com.example.filemanager.exception.ResourceNotFoundException;
import com.example.filemanager.repository.FileHistoryRepository;
import com.example.filemanager.repository.FileRepository;
import com.example.filemanager.repository.FileSpecification;
import com.example.filemanager.repository.GroupRepository;
import com.example.filemanager.repository.UserRepository;
import io.awspring.cloud.s3.S3Template;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.lang.NonNull;
import java.util.Objects;

@Service
public class FileService {

  private final FileRepository fileRepository;
  private final FileHistoryRepository fileHistoryRepository;
  private final S3Template s3Template;
  private final PermissionService permissionService;
  private final UserRepository userRepository;
  private final GroupRepository groupRepository;

  private final String bucketName;

  public FileService(
      FileRepository fileRepository,
      FileHistoryRepository fileHistoryRepository,
      S3Template s3Template,
      PermissionService permissionService,
      UserRepository userRepository,
      GroupRepository groupRepository,
      @Value("${S3_BUCKET_NAME}") String bucketName) {
    this.fileRepository = fileRepository;
    this.fileHistoryRepository = fileHistoryRepository;
    this.s3Template = s3Template;
    this.permissionService = permissionService;
    this.userRepository = userRepository;
    this.groupRepository = groupRepository;
    this.bucketName = bucketName;
  }

  @Transactional
  public FileEntity uploadFile(@NonNull MultipartFile file, Long parentFolderId, @NonNull String permissions)
      throws IOException {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

    FileEntity parent = null;
    if (parentFolderId != null) {
      parent = fileRepository
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

    Group group = currentUser
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
      // Store permissions as decimal integer (e.g., 755, 644)
      int perm = Integer.parseInt(permissions);
      // Validate each digit is 0-7
      if (perm < 0 || perm > 777 || !permissions.matches("[0-7]{3}")) {
        throw new InvalidPermissionFormatException(
            "Invalid permission format. Each digit must be 0-7 (e.g., '755').");
      }
      newFile.setPermissions(perm);
    } catch (NumberFormatException e) {
      throw new InvalidPermissionFormatException(
          "Invalid permission format. Please use a 3-digit number (e.g., '755').");
    }

    String originalFilename = Objects.requireNonNull(file.getOriginalFilename());
    String s3Key = UUID.randomUUID() + "/" + originalFilename;
    s3Template.upload(Objects.requireNonNull(bucketName), s3Key, file.getInputStream());
    newFile.setStorageKey(s3Key);

    return fileRepository.save(newFile);
  }

  @Transactional
  public FileEntity updateFile(@NonNull Long fileId, @NonNull MultipartFile file) throws IOException {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    FileEntity fileEntity = findFileById(fileId); // This already checks read permission

    if (!permissionService.canWrite(fileEntity, currentUser)) {
      throw new AccessDeniedException("You do not have permission to write to this file.");
    }

    checkFileLock(fileEntity, currentUser);

    if (fileEntity.isDirectory()) {
      throw new IllegalArgumentException("Cannot upload content to a directory.");
    }

    FileEntity parent = fileEntity.getParent();
    // Check if versioning is enabled on the parent folder
    if (parent != null && parent.getVersioningEnabled() != null && parent.getVersioningEnabled()) {
      // Versioning is enabled, create a history record
      int latestVersion = fileHistoryRepository
          .findByFileEntityIdOrderByVersionDesc(fileId)
          .stream()
          .findFirst()
          .map(FileHistory::getVersion)
          .orElse(0);

      FileHistory history = new FileHistory();
      history.setFileEntity(fileEntity);
      history.setModifier(currentUser);
      history.setStorageKey(fileEntity.getStorageKey()); // Old storage key
      history.setVersion(latestVersion + 1);
      fileHistoryRepository.save(history);

      // Upload new file to S3 with a new key
      String originalFilename = Objects.requireNonNull(file.getOriginalFilename());
      String newS3Key = UUID.randomUUID() + "/" + originalFilename;
      s3Template.upload(Objects.requireNonNull(bucketName), newS3Key, file.getInputStream());
      fileEntity.setStorageKey(newS3Key); // Update entity with the new key
    } else {
      // Versioning is not enabled, just overwrite the file in S3
      s3Template.upload(Objects.requireNonNull(bucketName), Objects.requireNonNull(fileEntity.getStorageKey()),
          file.getInputStream());
    }

    // Update the name in case it has changed
    fileEntity.setName(Objects.requireNonNull(file.getOriginalFilename()));
    return fileRepository.save(fileEntity);
  }

  public byte[] downloadFile(@NonNull FileEntity fileEntity) throws IOException {
    if (fileEntity.isDirectory()) {
      throw new IllegalArgumentException("Cannot download a directory.");
    }
    if (fileEntity.getStorageKey() == null) {
      // This case should ideally not happen for a file, but as a safeguard:
      throw new IllegalStateException("File entity is missing storage key.");
    }
    return s3Template.download(Objects.requireNonNull(bucketName), Objects.requireNonNull(fileEntity.getStorageKey()))
        .getInputStream()
        .readAllBytes();
  }

  @Transactional(readOnly = true)
  public FileEntity findFileById(@NonNull Long fileId) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    FileEntity fileEntity = fileRepository
        .findByIdAndDeletedAtIsNull(fileId)
        .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

    if (!permissionService.canRead(fileEntity, currentUser)) {
      throw new AccessDeniedException("You do not have permission to access this file.");
    }

    return fileEntity;
  }

  @Transactional(readOnly = true)
  public List<FileEntity> listFiles(Long parentId) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    FileEntity parent = null;
    if (parentId != null) {
      parent = fileRepository
          .findByIdAndDeletedAtIsNull(parentId)
          .orElseThrow(
              () -> new ResourceNotFoundException("Parent folder not found with id: " + parentId));
      if (!permissionService.canRead(parent, currentUser)) {
        throw new AccessDeniedException("You do not have permission to access this folder.");
      }
    }

    List<FileEntity> files = fileRepository.findAllByParentAndDeletedAtIsNull(parent);
    return files.stream()
        .filter(file -> permissionService.canRead(file, currentUser))
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public Page<FileEntity> listFiles(Long parentId, @NonNull Pageable pageable) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    FileEntity parent = null;
    if (parentId != null) {
      parent = fileRepository
          .findByIdAndDeletedAtIsNull(parentId)
          .orElseThrow(
              () -> new ResourceNotFoundException("Parent folder not found with id: " + parentId));
      if (!permissionService.canRead(parent, currentUser)) {
        throw new AccessDeniedException("You do not have permission to access this folder.");
      }
    }

    Page<FileEntity> filesPage = fileRepository.findAllByParentAndDeletedAtIsNull(parent, pageable);

    // Filter files based on read permission
    List<FileEntity> filteredFiles = filesPage.getContent().stream()
        .filter(file -> permissionService.canRead(file, currentUser))
        .collect(Collectors.toList());

    return new PageImpl<>(Objects.requireNonNull(filteredFiles), pageable, filesPage.getTotalElements());
  }

  @Transactional
  public FileEntity createDirectory(@NonNull FolderRequest request) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

    FileEntity parent = null;
    if (request.getParentFolderId() != null) {
      parent = fileRepository
          .findByIdAndDeletedAtIsNull(request.getParentFolderId())
          .orElseThrow(
              () -> new ResourceNotFoundException(
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
    Group group = currentUser
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

    // Store permissions as decimal integer (e.g., 755, 644)
    int perm = Integer.parseInt(request.getPermissions());
    if (perm < 0 || perm > 777 || !request.getPermissions().matches("[0-7]{3}")) {
      throw new InvalidPermissionFormatException(
          "Invalid permission format. Each digit must be 0-7 (e.g., '755').");
    }
    newDirectory.setPermissions(perm);

    return fileRepository.save(newDirectory);
  }

  @Transactional
  public void softDeleteFile(@NonNull Long fileId) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    FileEntity fileEntity = fileRepository
        .findByIdAndDeletedAtIsNull(fileId)
        .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

    if (!permissionService.canWrite(fileEntity, currentUser)) {
      throw new AccessDeniedException("You do not have permission to delete this file.");
    }

    checkFileLock(fileEntity, currentUser);

    fileEntity.setDeletedAt(LocalDateTime.now());
    fileRepository.save(fileEntity);
  }

  @Transactional
  public FileEntity renameFile(@NonNull Long fileId, @NonNull String newName) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    FileEntity fileEntity = fileRepository
        .findByIdAndDeletedAtIsNull(fileId)
        .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

    if (!permissionService.canWrite(fileEntity, currentUser)) {
      throw new AccessDeniedException("You do not have permission to rename this file.");
    }

    checkFileLock(fileEntity, currentUser);

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
    Specification<FileEntity> spec = FileSpecification.isNotDeleted();

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

  @Transactional
  public FileEntity moveFile(@NonNull Long fileId, @NonNull Long newParentId) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    FileEntity fileToMove = fileRepository
        .findByIdAndDeletedAtIsNull(fileId)
        .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

    if (!permissionService.canWrite(fileToMove, currentUser)) {
      throw new AccessDeniedException("You do not have permission to move this file.");
    }

    checkFileLock(fileToMove, currentUser);

    FileEntity destinationFolder = fileRepository
        .findByIdAndDeletedAtIsNull(newParentId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Destination folder not found with id: " + newParentId));

    if (!destinationFolder.isDirectory()) {
      throw new ParentNotDirectoryException(
          "Destination with id " + newParentId + " is not a directory.");
    }

    if (!permissionService.canWrite(destinationFolder, currentUser)) {
      throw new AccessDeniedException(
          "You do not have permission to move files into the destination folder.");
    }

    fileRepository
        .findByParentAndNameAndDeletedAtIsNull(destinationFolder, fileToMove.getName())
        .ifPresent(
            f -> {
              throw new DuplicateFileException(
                  "A file or directory with the name '"
                      + fileToMove.getName()
                      + "' already exists in the destination folder.");
            });

    fileToMove.setParent(destinationFolder);
    return fileRepository.save(fileToMove);
  }

  @Transactional(readOnly = true)
  public List<FileEntity> listDeletedFiles() {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    List<FileEntity> deletedFiles = fileRepository.findAllByDeletedAtIsNotNull();

    // Filter the list to only include files the user has permission to read
    return deletedFiles.stream()
        .filter(file -> permissionService.canRead(file, currentUser))
        .collect(Collectors.toList());
  }

  @Transactional
  public FileEntity restoreFile(@NonNull Long fileId) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    FileEntity fileEntity = fileRepository
        .findByIdAndDeletedAtIsNotNull(fileId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Deleted file not found with id: " + fileId));

    // Check if the user has write permission on the file to restore it
    if (!permissionService.canWrite(fileEntity, currentUser)) {
      throw new AccessDeniedException("You do not have permission to restore this file.");
    }

    fileEntity.setDeletedAt(null);
    return fileRepository.save(fileEntity);
  }

  @Transactional
  public FileEntity toggleVersioning(@NonNull Long folderId, boolean enable) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    FileEntity folder = findFileById(folderId); // This checks for existence and read permission

    if (!folder.isDirectory()) {
      throw new IllegalArgumentException("Versioning can only be enabled on directories.");
    }

    if (!permissionService.canWrite(folder, currentUser)) {
      throw new AccessDeniedException("You do not have permission to modify this folder.");
    }

    folder.setVersioningEnabled(enable);
    return fileRepository.save(folder);
  }

  @Transactional(readOnly = true)
  public List<FileHistory> getFileVersions(@NonNull Long fileId) {

    FileEntity fileEntity = findFileById(fileId); // Checks read permission

    if (fileEntity.isDirectory()) {
      throw new IllegalArgumentException("Cannot get versions for a directory.");
    }

    return fileHistoryRepository.findByFileEntityIdOrderByVersionDesc(fileId);
  }

  @Transactional
  public FileEntity restoreFileVersion(@NonNull Long fileId, @NonNull Long versionId) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    FileEntity fileEntity = findFileById(fileId); // Checks read permission

    if (!permissionService.canWrite(fileEntity, currentUser)) {
      throw new AccessDeniedException("You do not have permission to write to this file.");
    }

    FileHistory history = fileHistoryRepository
        .findById(versionId)
        .orElseThrow(
            () -> new ResourceNotFoundException("File version not found with id: " + versionId));

    // Create a new history entry for the current state before restoring
    int latestVersion = fileHistoryRepository
        .findByFileEntityIdOrderByVersionDesc(fileId)
        .stream()
        .findFirst()
        .map(FileHistory::getVersion)
        .orElse(0);

    FileHistory currentVersionHistory = new FileHistory();
    currentVersionHistory.setFileEntity(fileEntity);
    currentVersionHistory.setModifier(currentUser);
    currentVersionHistory.setStorageKey(fileEntity.getStorageKey());
    currentVersionHistory.setVersion(latestVersion + 1);
    fileHistoryRepository.save(currentVersionHistory);

    // Restore the old storage key
    fileEntity.setStorageKey(history.getStorageKey());
    return fileRepository.save(fileEntity);
  }

  @Transactional
  public void updateLockStatus(@NonNull Long fileId, boolean lock, @NonNull String username) {
    User currentUser = userRepository.findByUsername(username)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    FileEntity fileEntity = findFileById(fileId);

    if (fileEntity.isDirectory()) {
      throw new IllegalArgumentException("Cannot lock a directory.");
    }

    FileEntity parent = fileEntity.getParent();
    if (parent == null || parent.getVersioningEnabled() == null || !parent.getVersioningEnabled()) {
      throw new IllegalStateException("File lock can only be used for files in a version-controlled folder.");
    }

    if (!permissionService.canWrite(fileEntity, currentUser)) {
      throw new AccessDeniedException("You do not have permission to change the lock status of this file.");
    }

    if (lock) {
      if (fileEntity.isLocked() && !fileEntity.getLockedBy().equals(currentUser)) {
        throw new FileLockedException("File is already locked by another user.");
      }
      fileEntity.setLocked(true);
      fileEntity.setLockedBy(currentUser);
      fileEntity.setLockedAt(LocalDateTime.now());
    } else {
      if (!fileEntity.isLocked()) {
        // Optionally, handle the case where an unlock is attempted on an already
        // unlocked file.
        // For now, we'll just let it proceed silently.
        return;
      }
      if (!fileEntity.getLockedBy().equals(currentUser)) {
        throw new AccessDeniedException("You cannot unlock a file locked by another user.");
      }
      fileEntity.setLocked(false);
      fileEntity.setLockedBy(null);
      fileEntity.setLockedAt(null);
    }

    fileRepository.save(fileEntity);
  }

  @Transactional
  public FileEntity changePermissions(@NonNull Long fileId, @NonNull String newPermissions) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    FileEntity fileEntity = fileRepository
        .findByIdAndDeletedAtIsNull(fileId)
        .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

    // Only the owner can change permissions
    if (!fileEntity.getOwner().getId().equals(currentUser.getId())) {
      throw new AccessDeniedException("Only the owner can change permissions.");
    }

    try {
      // Parse as decimal integer (e.g., 755, 644)
      int permissions = Integer.parseInt(newPermissions);
      // Validate that each digit is 0-7 and it's a 3-digit number
      if (permissions < 0 || permissions > 777 || !newPermissions.matches("[0-7]{3}")) {
        throw new InvalidPermissionFormatException("Each digit must be 0-7 (e.g., '755').");
      }
      fileEntity.setPermissions(permissions);
    } catch (NumberFormatException e) {
      throw new InvalidPermissionFormatException(
          "Invalid permission format. Please use a 3-digit number (e.g., '755').");
    }

    return fileRepository.save(fileEntity);
  }

  @Transactional(readOnly = true)
  public List<FileEntity> getBreadcrumbs(Long folderId) {
    if (folderId == null) {
      return new ArrayList<>();
    }
    // findFileById checks for read permission on the folder itself
    FileEntity folder = findFileById(folderId);

    List<FileEntity> breadcrumbs = new ArrayList<>();
    FileEntity current = folder;
    while (current != null) {
      breadcrumbs.add(0, current);
      current = current.getParent();
    }
    return breadcrumbs;
  }

  private void checkFileLock(FileEntity fileEntity, User currentUser) {
    if (fileEntity.isLocked() && (fileEntity.getLockedBy() == null || !fileEntity.getLockedBy().equals(currentUser))) {
      throw new FileLockedException("File is locked by another user and cannot be modified.");
    }
  }

  @Transactional
  public FileEntity changeOwner(@NonNull Long fileId, @NonNull Long newOwnerId, @NonNull Long newGroupId,
      boolean recursive) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

    // Check if current user is admin
    boolean isAdmin = currentUser.getGroups().stream()
        .anyMatch(g -> "admins".equals(g.getName()));

    if (!isAdmin) {
      throw new AccessDeniedException("Only admins can change file ownership.");
    }

    FileEntity fileEntity = fileRepository
        .findByIdAndDeletedAtIsNull(fileId)
        .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

    User newOwner = userRepository.findById(newOwnerId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + newOwnerId));

    Group newGroup = groupRepository.findById(newGroupId)
        .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + newGroupId));

    fileEntity.setOwner(newOwner);
    fileEntity.setGroup(newGroup);

    FileEntity savedFile = fileRepository.save(fileEntity);

    if (recursive && fileEntity.isDirectory()) {
      changeOwnerRecursive(fileEntity, newOwner, newGroup);
    }

    return savedFile;
  }

  private void changeOwnerRecursive(FileEntity parent, User newOwner, Group newGroup) {
    List<FileEntity> children = fileRepository.findAllByParentAndDeletedAtIsNull(parent);
    for (FileEntity child : children) {
      child.setOwner(newOwner);
      child.setGroup(newGroup);
      fileRepository.save(child);
      if (child.isDirectory()) {
        changeOwnerRecursive(child, newOwner, newGroup);
      }
    }
  }

  @Transactional
  public FileEntity updateTags(@NonNull Long fileId, @NonNull String tags) {
    User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    FileEntity fileEntity = fileRepository
        .findByIdAndDeletedAtIsNull(fileId)
        .orElseThrow(() -> new ResourceNotFoundException("File not found with id: " + fileId));

    if (!permissionService.canWrite(fileEntity, currentUser)) {
      throw new AccessDeniedException("You do not have permission to modify tags for this file.");
    }

    checkFileLock(fileEntity, currentUser);

    fileEntity.setCustomTags(tags);
    return fileRepository.save(fileEntity);
  }
}
