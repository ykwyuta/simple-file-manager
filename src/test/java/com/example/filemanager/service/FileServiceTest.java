package com.example.filemanager.service;

import com.example.filemanager.controller.dto.FolderRequest;
import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.FileHistory;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.User;
import com.example.filemanager.exception.DuplicateFileException;
import com.example.filemanager.exception.ResourceNotFoundException;
import com.example.filemanager.repository.FileHistoryRepository;
import com.example.filemanager.repository.FileRepository;
import com.example.filemanager.repository.GroupRepository;
import com.example.filemanager.repository.UserRepository;
import com.example.filemanager.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class FileServiceTest {

  @Mock
  private FileRepository fileRepository;

  @Mock
  private FileHistoryRepository fileHistoryRepository;

  @Mock
  private FileStorage fileStorage;

  @Mock
  private UserRepository userRepository;

  @Mock
  private GroupRepository groupRepository;

  @Mock
  private PermissionService permissionService;

  @InjectMocks
  private FileService fileService;

  private User testUser;
  private Group testGroup;

  @BeforeEach
  void setUp() {
    testUser = new User();
    testUser.setId(1L);
    testUser.setUsername("testuser");

    testGroup = new Group();
    testGroup.setId(1L);
    testGroup.setName("testgroup");

    testUser.setGroups(Set.of(testGroup));

  }

  private void setupAuthentication() {
    Authentication authentication = mock(Authentication.class);
    SecurityContext securityContext = mock(SecurityContext.class);
    when(securityContext.getAuthentication()).thenReturn(authentication);
    when(authentication.getPrincipal()).thenReturn(testUser);
    SecurityContextHolder.setContext(securityContext);
  }

  @Test
  void createDirectory_Success_RootFolder() {
    setupAuthentication();
    // Given
    FolderRequest request = new FolderRequest();
    request.setName("Documents");
    request.setParentFolderId(null);
    request.setPermissions("755");

    when(fileRepository.findByParentAndNameAndDeletedAtIsNull(null, "Documents"))
        .thenReturn(Optional.empty());
    when(fileRepository.save(any(FileEntity.class)))
        .thenAnswer(
            invocation -> {
              FileEntity entity = invocation.getArgument(0);
              entity.setId(1L);
              return entity;
            });

    // When
    FileEntity result = fileService.createDirectory(request);

    // Then
    assertNotNull(result);
    assertEquals("Documents", result.getName());
    assertTrue(result.isDirectory());
    assertNull(result.getParent());
    verify(fileRepository, times(1)).save(any(FileEntity.class));
  }

  @Test
  void createDirectory_Success_SubFolder() {
    setupAuthentication();
    // Given
    FileEntity parentFolder = new FileEntity();
    parentFolder.setId(1L);
    parentFolder.setName("Root");
    parentFolder.setDirectory(true);

    FolderRequest request = new FolderRequest();
    request.setName("Images");
    request.setParentFolderId(1L);
    request.setPermissions("750");

    when(fileRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(parentFolder));
    // Creating inside a folder now requires write permission on that folder.
    when(permissionService.canWrite(parentFolder, testUser)).thenReturn(true);
    when(fileRepository.findByParentAndNameAndDeletedAtIsNull(parentFolder, "Images"))
        .thenReturn(Optional.empty());
    when(fileRepository.save(any(FileEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    FileEntity result = fileService.createDirectory(request);

    // Then
    assertNotNull(result);
    assertEquals("Images", result.getName());
    assertTrue(result.isDirectory());
    assertEquals(parentFolder, result.getParent());
  }

  @Test
  void createDirectory_Failure_ParentNotFound() {
    setupAuthentication();
    // Given
    FolderRequest request = new FolderRequest();
    request.setName("Music");
    request.setParentFolderId(99L);
    request.setPermissions("700");

    when(fileRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

    // When & Then
    assertThrows(ResourceNotFoundException.class, () -> fileService.createDirectory(request));
    verify(fileRepository, never()).save(any());
  }

  @Test
  void createDirectory_Failure_DuplicateName() {
    setupAuthentication();
    // Given
    FileEntity existingFile = new FileEntity();
    existingFile.setName("Documents");

    FolderRequest request = new FolderRequest();
    request.setName("Documents");
    request.setParentFolderId(null);
    request.setPermissions("755");

    when(fileRepository.findByParentAndNameAndDeletedAtIsNull(null, "Documents"))
        .thenReturn(Optional.of(existingFile));

    // When & Then
    assertThrows(DuplicateFileException.class, () -> fileService.createDirectory(request));
    verify(fileRepository, never()).save(any());
  }

  @Test
  void uploadFile_Success() throws IOException {
    setupAuthentication();
    // Given
    MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test data".getBytes());
    Long parentFolderId = null;
    String permissions = "644";

    when(fileRepository.findByParentAndNameAndDeletedAtIsNull(null, "test.txt"))
        .thenReturn(Optional.empty());
    when(fileRepository.save(any(FileEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    fileService.uploadFile(file, parentFolderId, permissions);

    // Then
    ArgumentCaptor<FileEntity> fileEntityCaptor = ArgumentCaptor.forClass(FileEntity.class);
    verify(fileRepository, times(1)).save(fileEntityCaptor.capture());
    FileEntity savedEntity = fileEntityCaptor.getValue();

    assertEquals("test.txt", savedEntity.getName());
    assertFalse(savedEntity.isDirectory());
    assertNotNull(savedEntity.getStorageKey());
    assertEquals(testUser, savedEntity.getOwner());
    // Stored as the three decimal digits shown in the UI (644), not octal 0644.
    assertEquals(644, savedEntity.getPermissions());


    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(fileStorage, times(1)).upload(keyCaptor.capture(), any(java.io.InputStream.class));
    // The key is an opaque UUID: the display name lives in the database, not
    // in the storage path.
    assertFalse(keyCaptor.getValue().contains("test.txt"));
    assertDoesNotThrow(() -> java.util.UUID.fromString(keyCaptor.getValue()));
  }

  @Test
  void uploadFile_Failure_ParentNotFound() throws IOException {
    setupAuthentication();
    // Given
    MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test data".getBytes());
    Long parentFolderId = 99L;
    String permissions = "644";

    when(fileRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

    // When & Then
    assertThrows(
        ResourceNotFoundException.class,
        () -> fileService.uploadFile(file, parentFolderId, permissions));
    verify(fileRepository, never()).save(any());
    verify(fileStorage, never()).upload(anyString(), any());
  }

  @Test
  void uploadFile_Failure_DuplicateName() throws IOException {
    setupAuthentication();
    // Given
    MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test data".getBytes());
    Long parentFolderId = null;
    String permissions = "644";

    when(fileRepository.findByParentAndNameAndDeletedAtIsNull(null, "test.txt"))
        .thenReturn(Optional.of(new FileEntity()));

    // When & Then
    assertThrows(
        DuplicateFileException.class,
        () -> fileService.uploadFile(file, parentFolderId, permissions));
    verify(fileRepository, never()).save(any());
    verify(fileStorage, never()).upload(anyString(), any());
  }

  @Test
  void downloadFile_Success() throws IOException {
    // Given
    FileEntity fileEntity = new FileEntity();
    fileEntity.setId(1L);
    fileEntity.setName("test.txt");
    fileEntity.setDirectory(false);
    fileEntity.setStorageKey("some-key/test.txt");

    byte[] fileContent = "test data".getBytes();


    // Note: We no longer need to mock findById for this test as the entity is
    // passed directly.
    when(fileStorage.download("some-key/test.txt")).thenReturn(fileContent);

    // When
    byte[] result = fileService.downloadFile(fileEntity);

    // Then
    assertArrayEquals(fileContent, result);
  }

  @Test
  void findFileById_Failure_NotFound() {
    // Given
    when(fileRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

    // When & Then
    assertThrows(ResourceNotFoundException.class, () -> fileService.findFileById(99L));
  }

  @Test
  void downloadFile_Failure_IsDirectory() {
    // Given
    FileEntity directoryEntity = new FileEntity();
    directoryEntity.setId(1L);
    directoryEntity.setName("documents");
    directoryEntity.setDirectory(true);

    // No need to mock findById, we pass the entity directly

    // When & Then
    assertThrows(IllegalArgumentException.class, () -> fileService.downloadFile(directoryEntity));
  }

  @Test
  void uploadFile_Failure_InvalidPermissions() throws IOException {
    setupAuthentication();
    // Given
    MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test data".getBytes());
    Long parentFolderId = null;
    String permissions = "invalid"; // Not an octal string

    // When & Then
    assertThrows(
        com.example.filemanager.exception.InvalidPermissionFormatException.class,
        () -> fileService.uploadFile(file, parentFolderId, permissions));
    verify(fileRepository, never()).save(any());
    verify(fileStorage, never()).upload(anyString(), any());
  }

  @Test
  void findFileById_Success_HasPermission() {
    setupAuthentication();
    // Given
    FileEntity fileEntity = new FileEntity();
    fileEntity.setId(1L);
    when(fileRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(fileEntity));
    when(permissionService.canRead(fileEntity, testUser)).thenReturn(true);

    // When
    FileEntity result = fileService.findFileById(1L);

    // Then
    assertNotNull(result);
    assertEquals(1L, result.getId());
  }

  @Test
  void findFileById_Failure_NoPermission() {
    setupAuthentication();
    // Given
    FileEntity fileEntity = new FileEntity();
    fileEntity.setId(1L);
    when(fileRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(fileEntity));
    when(permissionService.canRead(fileEntity, testUser)).thenReturn(false);

    // When & Then
    assertThrows(
        org.springframework.security.access.AccessDeniedException.class,
        () -> fileService.findFileById(1L));
  }

  @Test
  void softDeleteFile_Success() {
    setupAuthentication();
    // Given
    FileEntity fileEntity = new FileEntity();
    fileEntity.setId(1L);
    when(fileRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(fileEntity));
    when(permissionService.canWrite(fileEntity, testUser)).thenReturn(true);

    // When
    fileService.softDeleteFile(1L);

    // Then
    ArgumentCaptor<FileEntity> fileEntityCaptor = ArgumentCaptor.forClass(FileEntity.class);
    verify(fileRepository, times(1)).save(fileEntityCaptor.capture());
    assertNotNull(fileEntityCaptor.getValue().getDeletedAt());
  }

  @Test
  void softDeleteFile_Failure_NotFound() {
    setupAuthentication();
    // Given
    when(fileRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

    // When & Then
    assertThrows(ResourceNotFoundException.class, () -> fileService.softDeleteFile(99L));
    verify(fileRepository, never()).save(any());
  }

  @Test
  void softDeleteFile_Failure_NoPermission() {
    setupAuthentication();
    // Given
    FileEntity fileEntity = new FileEntity();
    fileEntity.setId(1L);
    when(fileRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(fileEntity));
    when(permissionService.canWrite(fileEntity, testUser)).thenReturn(false);

    // When & Then
    assertThrows(
        org.springframework.security.access.AccessDeniedException.class,
        () -> fileService.softDeleteFile(1L));
    verify(fileRepository, never()).save(any());
  }

  @Test
  void searchFiles_Success_WithNameAndTags() {
    setupAuthentication();
    // Given
    FileEntity file1 = new FileEntity();
    file1.setId(1L);
    file1.setName("document.pdf");
    file1.setCustomTags("work, report");

    FileEntity file2 = new FileEntity();
    file2.setId(2L);
    file2.setName("another-document.pdf");
    file2.setCustomTags("personal");

    List<FileEntity> foundFiles = Arrays.asList(file1, file2);

    when(fileRepository.findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any()))
        .thenReturn(foundFiles);

    // When
    List<FileEntity> results = fileService.searchFiles("document", "work");

    // Then: the name, tag and permission predicates are all in the
    // specification, so the service returns what the query returned.
    assertEquals(2, results.size());
  }

  @Test
  void searchFiles_Success_PermissionFiltering() {
    setupAuthentication();
    // Given
    FileEntity file1 = new FileEntity(); // has permission
    file1.setId(1L);
    file1.setName("report.docx");

    FileEntity file2 = new FileEntity(); // no permission
    file2.setId(2L);
    file2.setName("secret-report.docx");

    List<FileEntity> foundFiles = Arrays.asList(file1, file2);

    // The specification carries the permission predicate, so unreadable rows
    // never come back from the database in the first place.
    when(fileRepository.findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any())).thenReturn(List.of(file1));

    // When
    List<FileEntity> results = fileService.searchFiles("report", null);

    // Then
    assertEquals(1, results.size());
    assertEquals(file1.getId(), results.get(0).getId());
  }

  @Test
  void searchFiles_Success_NoResults() {
    setupAuthentication();
    // Given
    when(fileRepository.findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any()))
        .thenReturn(Collections.emptyList());

    // When
    List<FileEntity> results = fileService.searchFiles("nonexistent", "whatever");

    // Then
    assertTrue(results.isEmpty());
  }

  @Test
  void renameFile_Success() {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    String newName = "renamed-document.txt";
    FileEntity fileEntity = new FileEntity();
    fileEntity.setId(fileId);
    fileEntity.setName("original-document.txt");
    fileEntity.setParent(null);

    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(fileEntity));
    when(permissionService.canWrite(fileEntity, testUser)).thenReturn(true);
    when(fileRepository.findByParentAndNameAndDeletedAtIsNull(null, newName)).thenReturn(Optional.empty());
    when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // When
    FileEntity result = fileService.renameFile(fileId, newName);

    // Then
    assertNotNull(result);
    assertEquals(newName, result.getName());
    verify(fileRepository, times(1)).save(fileEntity);
  }

  @Test
  void renameFile_Failure_NotFound() {
    setupAuthentication();
    // Given
    Long fileId = 99L;
    String newName = "new-name.txt";
    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.empty());

    // When & Then
    assertThrows(ResourceNotFoundException.class, () -> fileService.renameFile(fileId, newName));
    verify(fileRepository, never()).save(any());
  }

  @Test
  void renameFile_Failure_NoPermission() {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    String newName = "new-name.txt";
    FileEntity fileEntity = new FileEntity();
    fileEntity.setId(fileId);

    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(fileEntity));
    when(permissionService.canWrite(fileEntity, testUser)).thenReturn(false);

    // When & Then
    assertThrows(org.springframework.security.access.AccessDeniedException.class,
        () -> fileService.renameFile(fileId, newName));
    verify(fileRepository, never()).save(any());
  }

  @Test
  void renameFile_Failure_DuplicateName() {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    String newName = "existing-name.txt";
    FileEntity fileEntity = new FileEntity();
    fileEntity.setId(fileId);
    fileEntity.setParent(null);

    FileEntity existingFile = new FileEntity();
    existingFile.setId(2L);
    existingFile.setName(newName);

    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(fileEntity));
    when(permissionService.canWrite(fileEntity, testUser)).thenReturn(true);
    when(fileRepository.findByParentAndNameAndDeletedAtIsNull(null, newName)).thenReturn(Optional.of(existingFile));

    // When & Then
    assertThrows(DuplicateFileException.class, () -> fileService.renameFile(fileId, newName));
    verify(fileRepository, never()).save(any());
  }

  @Test
  void moveFile_Success() {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    Long newParentId = 2L;

    FileEntity fileToMove = new FileEntity();
    fileToMove.setId(fileId);
    fileToMove.setName("file-to-move.txt");

    FileEntity destinationFolder = new FileEntity();
    destinationFolder.setId(newParentId);
    destinationFolder.setName("destination");
    destinationFolder.setDirectory(true);

    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(fileToMove));
    when(permissionService.canWrite(fileToMove, testUser)).thenReturn(true);
    when(fileRepository.findByIdAndDeletedAtIsNull(newParentId))
        .thenReturn(Optional.of(destinationFolder));
    when(permissionService.canWrite(destinationFolder, testUser)).thenReturn(true);
    when(fileRepository.findByParentAndNameAndDeletedAtIsNull(
        destinationFolder, fileToMove.getName()))
        .thenReturn(Optional.empty());
    when(fileRepository.save(any(FileEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // When
    FileEntity result = fileService.moveFile(fileId, newParentId);

    // Then
    assertNotNull(result);
    assertEquals(destinationFolder, result.getParent());
    verify(fileRepository, times(1)).save(fileToMove);
  }

  @Test
  void moveFile_Failure_DestinationIsFile() {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    Long newParentId = 2L;

    FileEntity fileToMove = new FileEntity();
    fileToMove.setId(fileId);

    FileEntity destination = new FileEntity(); // Not a directory
    destination.setId(newParentId);
    destination.setDirectory(false);

    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(fileToMove));
    when(permissionService.canWrite(fileToMove, testUser)).thenReturn(true);
    when(fileRepository.findByIdAndDeletedAtIsNull(newParentId)).thenReturn(Optional.of(destination));

    // When & Then
    assertThrows(
        com.example.filemanager.exception.ParentNotDirectoryException.class,
        () -> fileService.moveFile(fileId, newParentId));
    verify(fileRepository, never()).save(any());
  }

  @Test
  void moveFile_Failure_NoPermissionOnFile() {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    Long newParentId = 2L;

    FileEntity fileToMove = new FileEntity();
    fileToMove.setId(fileId);

    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(fileToMove));
    when(permissionService.canWrite(fileToMove, testUser)).thenReturn(false); // No permission

    // When & Then
    assertThrows(
        org.springframework.security.access.AccessDeniedException.class,
        () -> fileService.moveFile(fileId, newParentId));
  }

  @Test
  void moveFile_Failure_NoPermissionOnDestination() {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    Long newParentId = 2L;

    FileEntity fileToMove = new FileEntity();
    fileToMove.setId(fileId);

    FileEntity destinationFolder = new FileEntity();
    destinationFolder.setId(newParentId);
    destinationFolder.setDirectory(true);

    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(fileToMove));
    when(permissionService.canWrite(fileToMove, testUser)).thenReturn(true);
    when(fileRepository.findByIdAndDeletedAtIsNull(newParentId))
        .thenReturn(Optional.of(destinationFolder));
    when(permissionService.canWrite(destinationFolder, testUser)).thenReturn(false); // No permission

    // When & Then
    assertThrows(
        org.springframework.security.access.AccessDeniedException.class,
        () -> fileService.moveFile(fileId, newParentId));
  }

  @Test
  void moveFile_Failure_DuplicateNameInDestination() {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    Long newParentId = 2L;

    FileEntity fileToMove = new FileEntity();
    fileToMove.setId(fileId);
    fileToMove.setName("file.txt");

    FileEntity destinationFolder = new FileEntity();
    destinationFolder.setId(newParentId);
    destinationFolder.setDirectory(true);

    FileEntity existingFile = new FileEntity();
    existingFile.setName("file.txt");

    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(fileToMove));
    when(permissionService.canWrite(fileToMove, testUser)).thenReturn(true);
    when(fileRepository.findByIdAndDeletedAtIsNull(newParentId))
        .thenReturn(Optional.of(destinationFolder));
    when(permissionService.canWrite(destinationFolder, testUser)).thenReturn(true);
    when(fileRepository.findByParentAndNameAndDeletedAtIsNull(destinationFolder, "file.txt"))
        .thenReturn(Optional.of(existingFile));

    // When & Then
    assertThrows(DuplicateFileException.class, () -> fileService.moveFile(fileId, newParentId));
  }

  @Test
  void listDeletedFiles_ShowsOnlyTheRootsOfEachDeletedSubtree() {
    setupAuthentication();
    // Given: a deleted folder and a file that disappeared *with* it.
    java.time.LocalDateTime deletedAt = java.time.LocalDateTime.now();

    FileEntity deletedFolder = new FileEntity();
    deletedFolder.setId(1L);
    deletedFolder.setName("folder");
    deletedFolder.setDirectory(true);
    deletedFolder.setDeletedAt(deletedAt);

    FileEntity cascadedChild = new FileEntity();
    cascadedChild.setId(2L);
    cascadedChild.setName("child.txt");
    cascadedChild.setParent(deletedFolder);
    cascadedChild.setDeletedAt(deletedAt);

    // Readability is enforced by the specification, so the repository returns
    // only rows this user may see.
    when(fileRepository.findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any()))
        .thenReturn(Arrays.asList(deletedFolder, cascadedChild));

    // When
    List<FileEntity> result = fileService.listDeletedFiles();

    // Then: only the folder is offered, because restoring it brings the child back.
    assertEquals(1, result.size());
    assertEquals("folder", result.get(0).getName());
    verify(fileRepository, times(1)).findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any());
  }

  @Test
  void restoreFile_Success() {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    FileEntity fileToRestore = new FileEntity();
    fileToRestore.setId(fileId);
    fileToRestore.setDeletedAt(java.time.LocalDateTime.now());

    when(fileRepository.findByIdAndDeletedAtIsNotNull(fileId))
        .thenReturn(Optional.of(fileToRestore));
    when(permissionService.canWrite(fileToRestore, testUser)).thenReturn(true);
    when(fileRepository.save(any(FileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    FileEntity result = fileService.restoreFile(fileId);

    // Then
    assertNotNull(result);
    assertNull(result.getDeletedAt());
    verify(fileRepository, times(1)).save(fileToRestore);
  }

  @Test
  void restoreFile_Failure_NotFound() {
    setupAuthentication();
    // Given
    Long fileId = 99L;
    when(fileRepository.findByIdAndDeletedAtIsNotNull(fileId)).thenReturn(Optional.empty());

    // When & Then
    assertThrows(ResourceNotFoundException.class, () -> fileService.restoreFile(fileId));
    verify(fileRepository, never()).save(any());
  }

  @Test
  void restoreFile_Failure_NoPermission() {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    FileEntity fileToRestore = new FileEntity();
    fileToRestore.setId(fileId);
    fileToRestore.setDeletedAt(java.time.LocalDateTime.now());

    when(fileRepository.findByIdAndDeletedAtIsNotNull(fileId))
        .thenReturn(Optional.of(fileToRestore));
    when(permissionService.canWrite(fileToRestore, testUser)).thenReturn(false);

    // When & Then
    assertThrows(
        org.springframework.security.access.AccessDeniedException.class,
        () -> fileService.restoreFile(fileId));
    verify(fileRepository, never()).save(any());
  }

  @Test
  void toggleVersioning_Success() {
    setupAuthentication();
    // Given
    Long folderId = 1L;
    FileEntity folder = new FileEntity();
    folder.setId(folderId);
    folder.setDirectory(true);

    when(fileRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
    when(permissionService.canRead(folder, testUser)).thenReturn(true);
    when(permissionService.canWrite(folder, testUser)).thenReturn(true);
    when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    // When
    FileEntity result = fileService.toggleVersioning(folderId, true);

    // Then
    assertTrue(result.getVersioningEnabled());
    verify(fileRepository, times(1)).save(folder);
  }

  @Test
  void updateFile_Success_WithVersioning() throws IOException {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    MockMultipartFile file = new MockMultipartFile("file", "update.txt", "text/plain", "updated data".getBytes());
    FileEntity parent = new FileEntity();
    parent.setVersioningEnabled(true);
    FileEntity fileEntity = new FileEntity();
    fileEntity.setId(fileId);
    fileEntity.setParent(parent);
    fileEntity.setStorageKey("old-key");

    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(fileEntity));
    when(permissionService.canRead(fileEntity, testUser)).thenReturn(true);
    when(permissionService.canWrite(fileEntity, testUser)).thenReturn(true);
    when(fileHistoryRepository.findByFileEntityIdOrderByVersionDesc(fileId)).thenReturn(Collections.emptyList());

    // When
    fileService.updateFile(fileId, file);

    // Then
    // Then
    verify(fileHistoryRepository, times(1)).save(any(FileHistory.class));
    ArgumentCaptor<FileEntity> fileEntityCaptor = ArgumentCaptor.forClass(FileEntity.class);
    verify(fileRepository, times(1)).save(fileEntityCaptor.capture());
    assertNotEquals("old-key", fileEntityCaptor.getValue().getStorageKey());
    assertEquals("update.txt", fileEntityCaptor.getValue().getName());
  }

  @Test
  void listFiles_Success_Root() {
    setupAuthentication();
    // Given
    FileEntity file1 = new FileEntity();
    file1.setId(1L);
    file1.setName("file1.txt");
    file1.setParent(null);

    FileEntity file2 = new FileEntity();
    file2.setId(2L);
    file2.setName("file2.txt");
    file2.setParent(null);

    List<FileEntity> allFiles = Arrays.asList(file1, file2);

    // Permission filtering happens in the query now, so the repository hands
    // back exactly the rows the user may see.
    when(fileRepository.findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any())).thenReturn(allFiles);

    // When
    List<FileEntity> result = fileService.listFiles(null);

    // Then
    assertEquals(2, result.size());
    verify(fileRepository, times(1)).findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any());
  }

  @Test
  void listFiles_Success_SubFolder() {
    setupAuthentication();
    // Given
    Long parentId = 10L;
    FileEntity parent = new FileEntity();
    parent.setId(parentId);
    parent.setDirectory(true);

    FileEntity file1 = new FileEntity();
    file1.setId(1L);
    file1.setName("file1.txt");
    file1.setParent(parent);

    List<FileEntity> allFiles = Arrays.asList(file1);

    when(fileRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));
    when(permissionService.canRead(parent, testUser)).thenReturn(true);
    when(fileRepository.findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any())).thenReturn(allFiles);

    // When
    List<FileEntity> result = fileService.listFiles(parentId);

    // Then
    assertEquals(1, result.size());
    verify(fileRepository, times(1)).findAll(org.mockito.ArgumentMatchers.<Specification<FileEntity>>any());
  }

  @Test
  void listFiles_Failure_ParentNotFound() {
    setupAuthentication();
    // Given
    Long parentId = 99L;
    when(fileRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.empty());

    // When & Then
    assertThrows(ResourceNotFoundException.class, () -> fileService.listFiles(parentId));
  }

  @Test
  void listFiles_Failure_NoPermissionOnParent() {
    setupAuthentication();
    // Given
    Long parentId = 10L;
    FileEntity parent = new FileEntity();
    parent.setId(parentId);

    when(fileRepository.findByIdAndDeletedAtIsNull(parentId)).thenReturn(Optional.of(parent));
    when(permissionService.canRead(parent, testUser)).thenReturn(false);

    // When & Then
    assertThrows(org.springframework.security.access.AccessDeniedException.class,
        () -> fileService.listFiles(parentId));
  }

  @Test
  void updateFile_Success_WithoutVersioning() throws IOException {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    MockMultipartFile file = new MockMultipartFile("file", "update.txt", "text/plain", "updated data".getBytes());
    FileEntity parent = new FileEntity();
    parent.setVersioningEnabled(false);
    FileEntity fileEntity = new FileEntity();
    fileEntity.setId(fileId);
    fileEntity.setParent(parent);
    fileEntity.setStorageKey("old-key");

    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(fileEntity));
    when(permissionService.canRead(fileEntity, testUser)).thenReturn(true);
    when(permissionService.canWrite(fileEntity, testUser)).thenReturn(true);

    // When
    fileService.updateFile(fileId, file);

    // Then
    verify(fileHistoryRepository, never()).save(any());
    ArgumentCaptor<FileEntity> fileEntityCaptor = ArgumentCaptor.forClass(FileEntity.class);
    verify(fileRepository, times(1)).save(fileEntityCaptor.capture());
    assertEquals("old-key", fileEntityCaptor.getValue().getStorageKey());
  }

  @Test
  void getFileVersions_Success() {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    FileEntity fileEntity = new FileEntity();
    fileEntity.setId(fileId);

    FileHistory history1 = new FileHistory();
    history1.setVersion(1);
    FileHistory history2 = new FileHistory();
    history2.setVersion(2);

    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(fileEntity));
    when(permissionService.canRead(fileEntity, testUser)).thenReturn(true);
    when(fileHistoryRepository.findByFileEntityIdOrderByVersionDesc(fileId))
        .thenReturn(Arrays.asList(history2, history1));

    // When
    List<FileHistory> result = fileService.getFileVersions(fileId);

    // Then
    assertEquals(2, result.size());
    assertEquals(2, result.get(0).getVersion());
  }

  @Test
  void restoreFileVersion_Success() {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    Long versionId = 2L;
    FileEntity fileEntity = new FileEntity();
    fileEntity.setId(fileId);
    fileEntity.setStorageKey("current-key");

    FileHistory historyToRestore = new FileHistory();
    historyToRestore.setId(versionId);
    historyToRestore.setStorageKey("restored-key");

    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(fileEntity));
    when(permissionService.canRead(fileEntity, testUser)).thenReturn(true);
    when(permissionService.canWrite(fileEntity, testUser)).thenReturn(true);
    when(fileHistoryRepository.findByIdAndFileEntityId(versionId, fileId))
        .thenReturn(Optional.of(historyToRestore));
    when(fileHistoryRepository.findByFileEntityIdOrderByVersionDesc(fileId)).thenReturn(Collections.emptyList());

    // When
    fileService.restoreFileVersion(fileId, versionId);

    // Then
    verify(fileHistoryRepository, times(1)).save(any(FileHistory.class));
    ArgumentCaptor<FileEntity> fileEntityCaptor = ArgumentCaptor.forClass(FileEntity.class);
    verify(fileRepository, times(1)).save(fileEntityCaptor.capture());
    assertEquals("restored-key", fileEntityCaptor.getValue().getStorageKey());
  }

  // Test Case 1.1
  @Test
  void updateFile_WhenVersioningIsEnabledMidway_CreatesHistoryFromThatPoint() throws IOException {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    MockMultipartFile fileV2 = new MockMultipartFile("file", "file.txt", "text/plain", "version 2".getBytes());

    FileEntity parent = new FileEntity();
    parent.setId(10L);
    parent.setVersioningEnabled(false); // Versioning is initially off

    FileEntity fileEntity = new FileEntity();
    fileEntity.setId(fileId);
    fileEntity.setName("file.txt");
    fileEntity.setParent(parent);
    fileEntity.setStorageKey("v1-key");

    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(fileEntity));
    when(permissionService.canRead(any(), any())).thenReturn(true);
    when(permissionService.canWrite(any(), any())).thenReturn(true);

    // Action 1: Enable versioning
    parent.setVersioningEnabled(true);

    // Action 2: Update the file
    when(fileHistoryRepository.findByFileEntityIdOrderByVersionDesc(fileId)).thenReturn(Collections.emptyList());
    fileService.updateFile(fileId, fileV2);

    // Then
    ArgumentCaptor<FileHistory> historyCaptor = ArgumentCaptor.forClass(FileHistory.class);
    verify(fileHistoryRepository).save(historyCaptor.capture());
    FileHistory savedHistory = historyCaptor.getValue();

    assertEquals(1, savedHistory.getVersion()); // First version recorded
    assertEquals("v1-key", savedHistory.getStorageKey()); // The *previous* content is saved to history

    ArgumentCaptor<FileEntity> fileEntityCaptor = ArgumentCaptor.forClass(FileEntity.class);
    verify(fileRepository).save(fileEntityCaptor.capture());
    assertNotEquals("v1-key", fileEntityCaptor.getValue().getStorageKey()); // Current version has new key
  }

  // Test Case 1.2 & 1.3
  @Test
  void updateFile_WhenVersioningIsDisabled_OverwritesFileAndPreservesHistory() throws IOException {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    FileEntity parent = new FileEntity();
    parent.setVersioningEnabled(true);

    FileEntity fileEntity = new FileEntity();
    fileEntity.setId(fileId);
    fileEntity.setParent(parent);
    fileEntity.setStorageKey("v2-key");

    FileHistory v1 = new FileHistory();
    v1.setVersion(1);
    v1.setStorageKey("v1-key");
    List<FileHistory> existingHistory = List.of(v1);

    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(fileEntity));
    when(permissionService.canRead(any(), any())).thenReturn(true);
    when(permissionService.canWrite(any(), any())).thenReturn(true);
    when(fileHistoryRepository.findByFileEntityIdOrderByVersionDesc(fileId)).thenReturn(existingHistory);

    // Action 1: Disable versioning
    parent.setVersioningEnabled(false);

    // Action 2: Update the file (v3)
    MockMultipartFile fileV3 = new MockMultipartFile("file", "update.txt", "text/plain", "version 3".getBytes());
    fileService.updateFile(fileId, fileV3);

    // Then 2: No new history should be created
    verify(fileHistoryRepository, never()).save(any(FileHistory.class));
    ArgumentCaptor<FileEntity> fileEntityCaptor = ArgumentCaptor.forClass(FileEntity.class);
    verify(fileRepository).save(fileEntityCaptor.capture());
    assertEquals("v2-key", fileEntityCaptor.getValue().getStorageKey()); // S3 object is overwritten

    // Then 3: Existing history is still accessible
    List<FileHistory> retrievedHistory = fileService.getFileVersions(fileId);
    assertEquals(1, retrievedHistory.size());
    assertEquals(1, retrievedHistory.get(0).getVersion());
  }

  // Test Case 2.1
  @Test
  void moveFile_FromVersionedToNonVersionedFolder_PreservesHistory() throws IOException {
    setupAuthentication();
    // Given
    FileEntity file = new FileEntity();
    file.setId(1L);
    file.setName("history-file.txt");
    file.setStorageKey("existing-key");
    FileHistory v1 = new FileHistory();
    v1.setVersion(1);

    FileEntity versionedFolder = new FileEntity();
    versionedFolder.setId(10L);
    versionedFolder.setVersioningEnabled(true);
    file.setParent(versionedFolder);

    FileEntity nonVersionedFolder = new FileEntity();
    nonVersionedFolder.setId(11L);
    nonVersionedFolder.setDirectory(true);
    nonVersionedFolder.setVersioningEnabled(false);

    when(fileRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(file));
    when(fileRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(nonVersionedFolder));
    when(permissionService.canWrite(any(), any())).thenReturn(true);
    when(permissionService.canRead(any(), any())).thenReturn(true);
    when(fileRepository.findByParentAndNameAndDeletedAtIsNull(any(), any())).thenReturn(Optional.empty());
    when(fileHistoryRepository.findByFileEntityIdOrderByVersionDesc(1L)).thenReturn(List.of(v1));
    when(fileRepository.save(any(FileEntity.class))).thenAnswer(i -> i.getArgument(0));

    // Action 1: Move the file
    fileService.moveFile(1L, 11L);

    // Action 2: Update the file in the new, non-versioned location
    MockMultipartFile update = new MockMultipartFile("file", "update.txt", "text/plain", "updated".getBytes());
    fileService.updateFile(1L, update);

    // Then: History is preserved, but no new history is created
    verify(fileHistoryRepository, never()).save(any(FileHistory.class));
    List<FileHistory> history = fileService.getFileVersions(1L);
    assertEquals(1, history.size());
    assertEquals(1, history.get(0).getVersion());
  }

  // Test Case 2.2
  @Test
  void moveFile_FromNonVersionedToVersionedFolder_StartsVersioningOnUpdate() throws IOException {
    setupAuthentication();
    // Given
    FileEntity file = new FileEntity();
    file.setId(1L);
    file.setName("new-file.txt");
    file.setStorageKey("original-key");

    FileEntity nonVersionedFolder = new FileEntity();
    nonVersionedFolder.setId(10L);
    nonVersionedFolder.setVersioningEnabled(false);
    file.setParent(nonVersionedFolder);

    FileEntity versionedFolder = new FileEntity();
    versionedFolder.setId(11L);
    versionedFolder.setDirectory(true);
    versionedFolder.setVersioningEnabled(true);

    when(fileRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(file));
    when(fileRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(versionedFolder));
    when(permissionService.canWrite(any(), any())).thenReturn(true);
    when(permissionService.canRead(any(), any())).thenReturn(true);
    when(fileRepository.findByParentAndNameAndDeletedAtIsNull(any(), any())).thenReturn(Optional.empty());
    when(fileHistoryRepository.findByFileEntityIdOrderByVersionDesc(1L)).thenReturn(Collections.emptyList());
    when(fileRepository.save(any(FileEntity.class))).thenReturn(file);

    // Action 1: Move the file
    fileService.moveFile(1L, 11L);

    // Action 2: Update the file in the new versioned location
    MockMultipartFile update = new MockMultipartFile("file", "update.txt", "text/plain", "updated".getBytes());
    fileService.updateFile(1L, update);

    // Then: First version history is created
    ArgumentCaptor<FileHistory> historyCaptor = ArgumentCaptor.forClass(FileHistory.class);
    verify(fileHistoryRepository).save(historyCaptor.capture());
    assertEquals(1, historyCaptor.getValue().getVersion());
    assertEquals("original-key", historyCaptor.getValue().getStorageKey());
  }

  // Test Case 2.3
  @Test
  void moveFile_VersionedFolderToAnotherLocation_MaintainsSettings() {
    setupAuthentication();
    // Given
    FileEntity versionedFolder = new FileEntity();
    versionedFolder.setId(1L);
    versionedFolder.setDirectory(true);
    versionedFolder.setVersioningEnabled(true);
    versionedFolder.setName("Versioned");

    FileEntity destinationFolder = new FileEntity();
    destinationFolder.setId(2L);
    destinationFolder.setDirectory(true);

    when(fileRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(versionedFolder));
    when(fileRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(destinationFolder));
    when(permissionService.canWrite(any(), any())).thenReturn(true);
    when(fileRepository.findByParentAndNameAndDeletedAtIsNull(any(), any())).thenReturn(Optional.empty());
    when(fileRepository.save(any(FileEntity.class))).thenReturn(versionedFolder);

    // When
    FileEntity result = fileService.moveFile(1L, 2L);

    // Then
    assertTrue(result.getVersioningEnabled());
    assertEquals(destinationFolder, result.getParent());
    verify(fileRepository).save(versionedFolder);
  }

  // Test Case 3.1
  @Test
  void restoreFile_WithHistory_Success() {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    FileEntity fileToRestore = new FileEntity();
    fileToRestore.setId(fileId);
    fileToRestore.setDeletedAt(java.time.LocalDateTime.now());
    FileHistory v1 = new FileHistory();
    v1.setVersion(1);

    when(fileRepository.findByIdAndDeletedAtIsNotNull(fileId)).thenReturn(Optional.of(fileToRestore));
    when(permissionService.canWrite(fileToRestore, testUser)).thenReturn(true);
    when(fileRepository.save(any(FileEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(fileHistoryRepository.findByFileEntityIdOrderByVersionDesc(fileId)).thenReturn(List.of(v1));
    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(fileToRestore));
    when(permissionService.canRead(fileToRestore, testUser)).thenReturn(true);

    // When
    FileEntity result = fileService.restoreFile(fileId);
    List<FileHistory> history = fileService.getFileVersions(fileId);

    // Then
    assertNull(result.getDeletedAt());
    assertEquals(1, history.size());
    verify(fileRepository).save(fileToRestore);
  }

  // Test Case 4.2
  @Test
  void restoreFileVersion_Failure_NoWritePermission() {
    setupAuthentication();
    // Given
    Long fileId = 1L;
    Long versionId = 2L;
    FileEntity fileEntity = new FileEntity();
    fileEntity.setId(fileId);

    when(fileRepository.findByIdAndDeletedAtIsNull(fileId)).thenReturn(Optional.of(fileEntity));
    when(permissionService.canRead(fileEntity, testUser)).thenReturn(true);
    when(permissionService.canWrite(fileEntity, testUser)).thenReturn(false); // No write permission

    // When & Then
    assertThrows(org.springframework.security.access.AccessDeniedException.class,
        () -> fileService.restoreFileVersion(fileId, versionId));

    verify(fileHistoryRepository, never()).findByIdAndFileEntityId(any(), any());
    verify(fileRepository, never()).save(any());
  }

  // Test Case 4.3
  @Test
  void toggleVersioning_Failure_NoWritePermission() {
    setupAuthentication();
    // Given
    Long folderId = 1L;
    FileEntity folder = new FileEntity();
    folder.setId(folderId);
    folder.setDirectory(true);

    when(fileRepository.findByIdAndDeletedAtIsNull(folderId)).thenReturn(Optional.of(folder));
    when(permissionService.canRead(folder, testUser)).thenReturn(true);
    when(permissionService.canWrite(folder, testUser)).thenReturn(false); // No write permission

    // When & Then
    assertThrows(org.springframework.security.access.AccessDeniedException.class,
        () -> fileService.toggleVersioning(folderId, true));

    verify(fileRepository, never()).save(any());
  }
}
