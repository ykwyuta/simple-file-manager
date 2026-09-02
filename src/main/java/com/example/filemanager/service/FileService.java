package com.example.filemanager.service;

import com.example.filemanager.controller.dto.FolderRequest;
import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.User;
import com.example.filemanager.exception.DuplicateFileException;
import com.example.filemanager.exception.InvalidPermissionFormatException;
import com.example.filemanager.domain.FileHistory;
import com.example.filemanager.domain.Permission;
import com.example.filemanager.exception.InvalidNameException;
import com.example.filemanager.exception.ParentDeletedException;
import com.example.filemanager.exception.ParentNotDirectoryException;
import com.example.filemanager.exception.FileLockedException;
import com.example.filemanager.exception.ResourceNotFoundException;
import com.example.filemanager.repository.FileHistoryRepository;
import com.example.filemanager.repository.FileRepository;
import com.example.filemanager.repository.FileSpecification;
import com.example.filemanager.repository.GroupRepository;
import com.example.filemanager.repository.UserRepository;
import com.example.filemanager.storage.FileStorage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
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
  private final FileStorage fileStorage;
  private final PermissionService permissionService;
  private final UserRepository userRepository;
  private final GroupRepository groupRepository;


  /** Upper bound for file and folder names, matching the DB column. */
  static final int MAX_NAME_LENGTH = 255;

  public FileService(
      FileRepository fileRepository,
      FileHistoryRepository fileHistoryRepository,
      FileStorage fileStorage,
      PermissionService permissionService,
      UserRepository userRepository,
      GroupRepository groupRepository) {
    this.fileRepository = fileRepository;
    this.fileHistoryRepository = fileHistoryRepository;
    this.fileStorage = fileStorage;
    this.permissionService = permissionService;
    this.userRepository = userRepository;
    this.groupRepository = groupRepository;
  }

  /** The authenticated principal, as a {@link User}. */
  private User currentUser() {
    return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }

  /**
   * Validates a file or folder name.
   *
   * <p>
   * Names travel into S3 keys, Content-Disposition headers and the UI, so path
   * separators and the relative-path entries are rejected outright rather than
   * sanitised — a silently rewritten name is harder to reason about than a
   * refusal.
   */
  static String validateName(String rawName) {
    String name = rawName == null ? "" : rawName.trim();
    if (name.isEmpty()) {
      throw new InvalidNameException("名前を入力してください。");
    }
    if (name.length() > MAX_NAME_LENGTH) {
      throw new InvalidNameException("名前は" + MAX_NAME_LENGTH + "文字以内で入力してください。");
    }
    if (name.equals(".") || name.equals("..")) {
      throw new InvalidNameException("'.' および '..' は名前として使用できません。");
    }
    if (name.contains("/") || name.contains("\\")) {
      throw new InvalidNameException("名前にパス区切り文字 (/ \\) は使用できません。");
    }
    for (char c : name.toCharArray()) {
      if (c < 0x20 || c == 0x7F) {
        throw new InvalidNameException("名前に制御文字は使用できません。");
      }
    }
    return name;
  }

  /** Parses and validates a three-digit permission string such as "755". */
  static int parsePermissions(String permissions) {
    if (permissions == null || !permissions.matches("[0-7]{3}")) {
      throw new InvalidPermissionFormatException(
          "パーミッションは各桁が0〜7の3桁の数字で指定してください (例: '755')。");
    }
    return Integer.parseInt(permissions);
  }

  @Transactional
  public FileEntity uploadFile(@NonNull MultipartFile file, Long parentFolderId, @NonNull String permissions)
      throws IOException {
    User currentUser = currentUser();

    FileEntity parent = null;
    if (parentFolderId != null) {
      parent = fileRepository
          .findByIdAndDeletedAtIsNull(parentFolderId)
          .orElseThrow(
              () -> new ResourceNotFoundException("親フォルダ (id: " + parentFolderId + ") が見つかりません。"));
      if (!parent.isDirectory()) {
        throw new ParentNotDirectoryException(
            "指定された親 (id: " + parentFolderId + ") はフォルダではありません。");
      }
      if (!permissionService.canWrite(parent, currentUser)) {
        throw new AccessDeniedException("このフォルダにファイルを作成する権限がありません。");
      }
    }

    String fileName = validateName(file.getOriginalFilename());

    fileRepository
        .findByParentAndNameAndDeletedAtIsNull(parent, fileName)
        .ifPresent(
            f -> {
              throw new DuplicateFileException(
                  "'" + fileName + "' と同じ名前のファイルまたはフォルダが既に存在します。");
            });

    Group group = currentUser
        .getGroups()
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("ユーザーがどのグループにも所属していません。"));

    FileEntity newFile = new FileEntity();
    newFile.setName(fileName);
    newFile.setDirectory(false);
    newFile.setParent(parent);
    newFile.setOwner(currentUser);
    newFile.setGroup(group);
    newFile.setPermissions(parsePermissions(permissions));
    newFile.setSizeBytes(file.getSize());
    newFile.setContentType(file.getContentType());

    // The key is a bare UUID: putting the user-supplied name in the storage
    // path made keys depend on the platform's filename encoding and gave
    // untrusted input a say in where bytes land. The display name lives in the
    // database.
    String s3Key = UUID.randomUUID().toString();
    fileStorage.upload(s3Key, file.getInputStream());
    newFile.setStorageKey(s3Key);

    return fileRepository.save(newFile);
  }

  @Transactional
  public FileEntity updateFile(@NonNull Long fileId, @NonNull MultipartFile file) throws IOException {
    User currentUser = currentUser();
    FileEntity fileEntity = findFileById(fileId); // This already checks read permission

    if (!permissionService.canWrite(fileEntity, currentUser)) {
      throw new AccessDeniedException("このファイルを更新する権限がありません。");
    }

    checkFileLock(fileEntity, currentUser);

    if (fileEntity.isDirectory()) {
      throw new IllegalArgumentException("フォルダにファイル内容をアップロードすることはできません。");
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
      String newS3Key = UUID.randomUUID().toString();
      fileStorage.upload(newS3Key, file.getInputStream());
      fileEntity.setStorageKey(newS3Key); // Update entity with the new key
    } else {
      // Versioning is not enabled: overwrite in place. A file with no key yet
      // (a metadata row whose upload never completed) gets a fresh one rather
      // than failing with a null dereference.
      String key = fileEntity.getStorageKey();
      if (key == null || key.isEmpty()) {
        key = UUID.randomUUID().toString();
        fileEntity.setStorageKey(key);
      }
      fileStorage.upload(key, file.getInputStream());
    }

    // Update the name in case it has changed
    fileEntity.setName(validateName(file.getOriginalFilename()));
    fileEntity.setSizeBytes(file.getSize());
    fileEntity.setContentType(file.getContentType());
    return fileRepository.save(fileEntity);
  }

  public byte[] downloadFile(@NonNull FileEntity fileEntity) throws IOException {
    if (fileEntity.isDirectory()) {
      throw new IllegalArgumentException("フォルダはダウンロードできません。");
    }
    if (fileEntity.getStorageKey() == null) {
      // This case should ideally not happen for a file, but as a safeguard:
      throw new IllegalStateException("File entity is missing storage key.");
    }
    return fileStorage.download(Objects.requireNonNull(fileEntity.getStorageKey()));
  }

  @Transactional(readOnly = true)
  public FileEntity findFileById(@NonNull Long fileId) {
    User currentUser = currentUser();
    FileEntity fileEntity = fileRepository
        .findByIdAndDeletedAtIsNull(fileId)
        .orElseThrow(() -> new ResourceNotFoundException("ファイル (id: " + fileId + ") が見つかりません。"));

    if (!permissionService.canRead(fileEntity, currentUser)) {
      throw new AccessDeniedException("このファイルにアクセスする権限がありません。");
    }

    return fileEntity;
  }

  @Transactional(readOnly = true)
  public List<FileEntity> listFiles(Long parentId) {
    User currentUser = currentUser();
    FileEntity parent = null;
    if (parentId != null) {
      parent = fileRepository
          .findByIdAndDeletedAtIsNull(parentId)
          .orElseThrow(
              () -> new ResourceNotFoundException("フォルダ (id: " + parentId + ") が見つかりません。"));
      if (!permissionService.canRead(parent, currentUser)) {
        throw new AccessDeniedException("このフォルダにアクセスする権限がありません。");
      }
    }

    return fileRepository.findAll(
        FileSpecification.isNotDeleted()
            .and(FileSpecification.hasParent(parent))
            .and(FileSpecification.isReadableBy(currentUser)));
  }

  @Transactional(readOnly = true)
  public Page<FileEntity> listFiles(Long parentId, @NonNull Pageable pageable) {
    User currentUser = currentUser();
    FileEntity parent = null;
    if (parentId != null) {
      parent = fileRepository
          .findByIdAndDeletedAtIsNull(parentId)
          .orElseThrow(
              () -> new ResourceNotFoundException("フォルダ (id: " + parentId + ") が見つかりません。"));
      if (!permissionService.canRead(parent, currentUser)) {
        throw new AccessDeniedException("このフォルダにアクセスする権限がありません。");
      }
    }

    // Filtering happens in SQL so that the reported total matches what the user
    // can actually see. Paging first and filtering afterwards produces short
    // pages and an inflated item count.
    return fileRepository.findAll(
        FileSpecification.isNotDeleted()
            .and(FileSpecification.hasParent(parent))
            .and(FileSpecification.isReadableBy(currentUser)),
        pageable);
  }

  @Transactional
  public FileEntity createDirectory(@NonNull FolderRequest request) {
    User currentUser = currentUser();

    FileEntity parent = null;
    if (request.getParentFolderId() != null) {
      parent = fileRepository
          .findByIdAndDeletedAtIsNull(request.getParentFolderId())
          .orElseThrow(
              () -> new ResourceNotFoundException(
                  "親フォルダ (id: " + request.getParentFolderId() + ") が見つかりません。"));
      if (!parent.isDirectory()) {
        throw new ParentNotDirectoryException(
            "指定された親 (id: " + request.getParentFolderId() + ") はフォルダではありません。");
      }
      if (!permissionService.canWrite(parent, currentUser)) {
        throw new AccessDeniedException("このフォルダにフォルダを作成する権限がありません。");
      }
    }

    String folderName = validateName(request.getName());

    fileRepository
        .findByParentAndNameAndDeletedAtIsNull(parent, folderName)
        .ifPresent(
            f -> {
              throw new DuplicateFileException(
                  "'" + folderName + "' と同じ名前のファイルまたはフォルダが既に存在します。");
            });

    // The primary group of the user is used as the folder's group.
    // A more sophisticated implementation might allow selecting a group.
    Group group = currentUser
        .getGroups()
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("ユーザーがどのグループにも所属していません。"));

    FileEntity newDirectory = new FileEntity();
    newDirectory.setName(folderName);
    newDirectory.setDirectory(true);
    newDirectory.setParent(parent);
    newDirectory.setOwner(currentUser);
    newDirectory.setGroup(group);
    newDirectory.setPermissions(parsePermissions(request.getPermissions()));

    return fileRepository.save(newDirectory);
  }

  @Transactional
  public void softDeleteFile(@NonNull Long fileId) {
    User currentUser = currentUser();
    FileEntity fileEntity = fileRepository
        .findByIdAndDeletedAtIsNull(fileId)
        .orElseThrow(() -> new ResourceNotFoundException("ファイル (id: " + fileId + ") が見つかりません。"));

    if (!permissionService.canWrite(fileEntity, currentUser)) {
      throw new AccessDeniedException("このファイルを削除する権限がありません。");
    }

    checkFileLock(fileEntity, currentUser);

    // Stamping every descendant with the *same* timestamp records that they
    // went away as one unit, which is what lets restore put back exactly the
    // subtree this delete removed. Without the cascade the children stay
    // "not deleted": invisible in every listing and in the trash, still
    // downloadable by id, and never picked up by the hard-delete job.
    LocalDateTime deletedAt = LocalDateTime.now();
    softDeleteRecursive(fileEntity, deletedAt);
  }

  private void softDeleteRecursive(FileEntity entity, LocalDateTime deletedAt) {
    entity.setDeletedAt(deletedAt);
    fileRepository.save(entity);
    if (entity.isDirectory()) {
      for (FileEntity child : fileRepository.findAllByParentAndDeletedAtIsNull(entity)) {
        softDeleteRecursive(child, deletedAt);
      }
    }
  }

  @Transactional
  public FileEntity renameFile(@NonNull Long fileId, @NonNull String newName) {
    User currentUser = currentUser();
    FileEntity fileEntity = fileRepository
        .findByIdAndDeletedAtIsNull(fileId)
        .orElseThrow(() -> new ResourceNotFoundException("ファイル (id: " + fileId + ") が見つかりません。"));

    if (!permissionService.canWrite(fileEntity, currentUser)) {
      throw new AccessDeniedException("このファイルの名前を変更する権限がありません。");
    }

    checkFileLock(fileEntity, currentUser);

    String validated = validateName(newName);
    fileRepository
        .findByParentAndNameAndDeletedAtIsNull(fileEntity.getParent(), validated)
        .ifPresent(
            f -> {
              if (!f.getId().equals(fileEntity.getId())) {
                throw new DuplicateFileException(
                    "'" + validated + "' と同じ名前のファイルまたはフォルダが既に存在します。");
              }
            });

    fileEntity.setName(validated);
    return fileRepository.save(fileEntity);
  }

  @Transactional(readOnly = true)
  public List<FileEntity> searchFiles(String name, String tags) {
    User currentUser = currentUser();
    Specification<FileEntity> spec = FileSpecification.isNotDeleted();

    if (StringUtils.hasText(name)) {
      spec = spec.and(FileSpecification.nameContains(name));
    }
    if (StringUtils.hasText(tags)) {
      spec = spec.and(FileSpecification.tagsContain(tags));
    }

    return fileRepository.findAll(spec.and(FileSpecification.isReadableBy(currentUser)));
  }

  @Transactional
  public FileEntity moveFile(@NonNull Long fileId, @NonNull Long newParentId) {
    User currentUser = currentUser();
    FileEntity fileToMove = fileRepository
        .findByIdAndDeletedAtIsNull(fileId)
        .orElseThrow(() -> new ResourceNotFoundException("ファイル (id: " + fileId + ") が見つかりません。"));

    if (!permissionService.canWrite(fileToMove, currentUser)) {
      throw new AccessDeniedException("このファイルを移動する権限がありません。");
    }

    checkFileLock(fileToMove, currentUser);

    FileEntity destinationFolder = fileRepository
        .findByIdAndDeletedAtIsNull(newParentId)
        .orElseThrow(
            () -> new ResourceNotFoundException("移動先フォルダ (id: " + newParentId + ") が見つかりません。"));

    if (!destinationFolder.isDirectory()) {
      throw new ParentNotDirectoryException(
          "移動先 (id: " + newParentId + ") はフォルダではありません。");
    }

    if (!permissionService.canWrite(destinationFolder, currentUser)) {
      throw new AccessDeniedException(
          "移動先フォルダに書き込む権限がありません。");
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
    User currentUser = currentUser();
    List<FileEntity> deletedFiles = fileRepository.findAll(
        FileSpecification.isDeleted().and(FileSpecification.isReadableBy(currentUser)));

    // Show only the top of each deleted subtree. A child that vanished because
    // its parent was deleted is restored with the parent, so listing it
    // separately would offer a restore the user cannot actually perform.
    return deletedFiles.stream()
        .filter(file -> {
          FileEntity parent = file.getParent();
          return parent == null || parent.getDeletedAt() == null;
        })
        .collect(Collectors.toList());
  }

  @Transactional
  public FileEntity restoreFile(@NonNull Long fileId) {
    User currentUser = currentUser();
    FileEntity fileEntity = fileRepository
        .findByIdAndDeletedAtIsNotNull(fileId)
        .orElseThrow(
            () -> new ResourceNotFoundException("削除済みファイル (id: " + fileId + ") が見つかりません。"));

    // Check if the user has write permission on the file to restore it
    if (!permissionService.canWrite(fileEntity, currentUser)) {
      throw new AccessDeniedException("このファイルを復元する権限がありません。");
    }

    FileEntity parent = fileEntity.getParent();
    if (parent != null && parent.getDeletedAt() != null) {
      throw new ParentDeletedException(
          "親フォルダ '" + parent.getName() + "' が削除されています。先に親フォルダを復元してください。");
    }

    LocalDateTime deletedAt = fileEntity.getDeletedAt();
    restoreRecursive(fileEntity, deletedAt);
    return fileEntity;
  }

  /**
   * Restores the subtree that disappeared in the same delete.
   *
   * <p>
   * Children deleted separately <em>before</em> the parent carry a different
   * timestamp and stay in the trash, so restoring a folder never resurrects
   * something the user had already thrown away on its own.
   */
  private void restoreRecursive(FileEntity entity, LocalDateTime deletedAt) {
    List<FileEntity> cascaded = entity.isDirectory()
        ? fileRepository.findAllByParentAndDeletedAt(entity, deletedAt)
        : List.of();
    entity.setDeletedAt(null);
    fileRepository.save(entity);
    for (FileEntity child : cascaded) {
      restoreRecursive(child, deletedAt);
    }
  }

  @Transactional
  public FileEntity toggleVersioning(@NonNull Long folderId, boolean enable) {
    User currentUser = currentUser();
    FileEntity folder = findFileById(folderId); // This checks for existence and read permission

    if (!folder.isDirectory()) {
      throw new IllegalArgumentException("バージョン管理はフォルダにのみ設定できます。");
    }

    if (!permissionService.canWrite(folder, currentUser)) {
      throw new AccessDeniedException("このフォルダを変更する権限がありません。");
    }

    folder.setVersioningEnabled(enable);
    return fileRepository.save(folder);
  }

  @Transactional(readOnly = true)
  public List<FileHistory> getFileVersions(@NonNull Long fileId) {

    FileEntity fileEntity = findFileById(fileId); // Checks read permission

    if (fileEntity.isDirectory()) {
      throw new IllegalArgumentException("フォルダにはバージョン履歴がありません。");
    }

    return fileHistoryRepository.findByFileEntityIdOrderByVersionDesc(fileId);
  }

  @Transactional
  public FileEntity restoreFileVersion(@NonNull Long fileId, @NonNull Long versionId) {
    User currentUser = currentUser();
    FileEntity fileEntity = findFileById(fileId); // Checks read permission

    if (!permissionService.canWrite(fileEntity, currentUser)) {
      throw new AccessDeniedException("このファイルを更新する権限がありません。");
    }

    // Resolve the version *within this file*. Looking it up by id alone lets a
    // caller graft another user's stored object onto a file they own and read
    // its contents.
    FileHistory history = fileHistoryRepository
        .findByIdAndFileEntityId(versionId, fileId)
        .orElseThrow(
            () -> new ResourceNotFoundException(
                "バージョン (id: " + versionId + ") はこのファイルに存在しません。"));

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
  public void updateLockStatus(@NonNull Long fileId, boolean lock) {
    User currentUser = currentUser();
    FileEntity fileEntity = findFileById(fileId);

    if (fileEntity.isDirectory()) {
      throw new IllegalArgumentException("フォルダはロックできません。");
    }

    FileEntity parent = fileEntity.getParent();
    if (parent == null || parent.getVersioningEnabled() == null || !parent.getVersioningEnabled()) {
      throw new IllegalStateException("ロックはバージョン管理が有効なフォルダ内のファイルにのみ使用できます。");
    }

    if (!permissionService.canWrite(fileEntity, currentUser)) {
      throw new AccessDeniedException("このファイルのロック状態を変更する権限がありません。");
    }

    if (lock) {
      if (fileEntity.isLocked() && !isLockedBy(fileEntity, currentUser)) {
        throw new FileLockedException("このファイルは他のユーザーがロックしています。");
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
      if (!isLockedBy(fileEntity, currentUser)) {
        throw new AccessDeniedException("他のユーザーがロックしたファイルは解除できません。");
      }
      fileEntity.setLocked(false);
      fileEntity.setLockedBy(null);
      fileEntity.setLockedAt(null);
    }

    fileRepository.save(fileEntity);
  }

  @Transactional
  public FileEntity changePermissions(@NonNull Long fileId, @NonNull String newPermissions) {
    User currentUser = currentUser();
    FileEntity fileEntity = fileRepository
        .findByIdAndDeletedAtIsNull(fileId)
        .orElseThrow(() -> new ResourceNotFoundException("ファイル (id: " + fileId + ") が見つかりません。"));

    // Only the owner (or an administrator) can change permissions
    if (!permissionService.isAdmin(currentUser)
        && !fileEntity.getOwner().getId().equals(currentUser.getId())) {
      throw new AccessDeniedException("パーミッションを変更できるのは所有者のみです。");
    }

    fileEntity.setPermissions(parsePermissions(newPermissions));
    return fileRepository.save(fileEntity);
  }

  @Transactional(readOnly = true)
  public List<FileEntity> getBreadcrumbs(Long folderId) {
    if (folderId == null) {
      return new ArrayList<>();
    }
    // findFileById checks for read permission on the folder itself
    FileEntity folder = findFileById(folderId);

    User currentUser = currentUser();
    List<FileEntity> breadcrumbs = new ArrayList<>();
    FileEntity current = folder;
    // Stop at the first ancestor the user cannot read: a trail is a navigation
    // aid, and it should not name folders the user is not allowed to see.
    while (current != null && permissionService.canRead(current, currentUser)) {
      breadcrumbs.add(0, current);
      current = current.getParent();
    }
    return breadcrumbs;
  }

  private void checkFileLock(FileEntity fileEntity, User currentUser) {
    if (fileEntity.isLocked() && !isLockedBy(fileEntity, currentUser)) {
      throw new FileLockedException("このファイルは他のユーザーがロックしているため変更できません。");
    }
  }

  /**
   * Whether {@code user} holds the lock on {@code fileEntity}.
   *
   * <p>
   * Compares identifiers rather than instances. {@code getLockedBy()} returns a
   * Hibernate proxy, and comparing that to the authenticated principal by
   * reference was always false — so the lock holder could neither release their
   * own lock nor edit the file they had locked.
   */
  private boolean isLockedBy(FileEntity fileEntity, User user) {
    User lockedBy = fileEntity.getLockedBy();
    return lockedBy != null && user != null && lockedBy.getId().equals(user.getId());
  }

  @Transactional
  public FileEntity changeOwner(@NonNull Long fileId, @NonNull Long newOwnerId, @NonNull Long newGroupId,
      boolean recursive) {
    User currentUser = currentUser();

    if (!permissionService.isAdmin(currentUser)) {
      throw new AccessDeniedException("所有者を変更できるのは管理者のみです。");
    }

    FileEntity fileEntity = fileRepository
        .findByIdAndDeletedAtIsNull(fileId)
        .orElseThrow(() -> new ResourceNotFoundException("ファイル (id: " + fileId + ") が見つかりません。"));

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
    User currentUser = currentUser();
    FileEntity fileEntity = fileRepository
        .findByIdAndDeletedAtIsNull(fileId)
        .orElseThrow(() -> new ResourceNotFoundException("ファイル (id: " + fileId + ") が見つかりません。"));

    if (!permissionService.canWrite(fileEntity, currentUser)) {
      throw new AccessDeniedException("このファイルのタグを変更する権限がありません。");
    }

    checkFileLock(fileEntity, currentUser);

    fileEntity.setCustomTags(tags);
    return fileRepository.save(fileEntity);
  }
}
