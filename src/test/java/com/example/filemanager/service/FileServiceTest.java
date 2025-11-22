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
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
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
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

  @Mock private FileRepository fileRepository;

  @Mock private FileHistoryRepository fileHistoryRepository;

  @Mock private S3Template s3Template;

  @Mock private PermissionService permissionService;

  @InjectMocks private FileService fileService;

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

    // Inject the bucket name for tests
    ReflectionTestUtils.setField(fileService, "bucketName", "test-bucket");
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
    MockMultipartFile file =
        new MockMultipartFile("file", "test.txt", "text/plain", "test data".getBytes());
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
    assertEquals(0644, savedEntity.getPermissions());

    ArgumentCaptor<String> bucketNameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(s3Template, times(1))
        .upload(bucketNameCaptor.capture(), keyCaptor.capture(), any(java.io.InputStream.class));
    assertEquals("test-bucket", bucketNameCaptor.getValue());
    assertTrue(keyCaptor.getValue().endsWith("/test.txt"));
  }

  @Test
  void uploadFile_Failure_ParentNotFound() {
    setupAuthentication();
    // Given
    MockMultipartFile file =
        new MockMultipartFile("file", "test.txt", "text/plain", "test data".getBytes());
    Long parentFolderId = 99L;
    String permissions = "644";

    when(fileRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

    // When & Then
    assertThrows(
        ResourceNotFoundException.class,
        () -> fileService.uploadFile(file, parentFolderId, permissions));
    verify(fileRepository, never()).save(any());
    verify(s3Template, never()).upload(anyString(), anyString(), any());
  }

  @Test
  void uploadFile_Failure_DuplicateName() {
    setupAuthentication();
    // Given
    MockMultipartFile file =
        new MockMultipartFile("file", "test.txt", "text/plain", "test data".getBytes());
    Long parentFolderId = null;
    String permissions = "644";

    when(fileRepository.findByParentAndNameAndDeletedAtIsNull(null, "test.txt"))
        .thenReturn(Optional.of(new FileEntity()));

    // When & Then
    assertThrows(
        DuplicateFileException.class,
        () -> fileService.uploadFile(file, parentFolderId, permissions));
    verify(fileRepository, never()).save(any());
    verify(s3Template, never()).upload(anyString(), anyString(), any());
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
    S3Resource s3Resource = mock(S3Resource.class);

    // Note: We no longer need to mock findById for this test as the entity is passed directly.
    when(s3Template.download("test-bucket", "some-key/test.txt")).thenReturn(s3Resource);
    when(s3Resource.getInputStream()).thenReturn(new ByteArrayInputStream(fileContent));

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
  void uploadFile_Failure_InvalidPermissions() {
    setupAuthentication();
    // Given
    MockMultipartFile file =
        new MockMultipartFile("file", "test.txt", "text/plain", "test data".getBytes());
    Long parentFolderId = null;
    String permissions = "invalid"; // Not an octal string

    // When & Then
    assertThrows(
        com.example.filemanager.exception.InvalidPermissionFormatException.class,
        () -> fileService.uploadFile(file, parentFolderId, permissions));
    verify(fileRepository, never()).save(any());
    verify(s3Template, never()).upload(anyString(), anyString(), any());
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

    when(fileRepository.findAll(any(Specification.class))).thenReturn(foundFiles);
    when(permissionService.canRead(file1, testUser)).thenReturn(true);
    when(permissionService.canRead(file2, testUser)).thenReturn(true);

    // When
    List<FileEntity> results = fileService.searchFiles("document", "work");

    // Then
    assertEquals(
        2,
        results.size()); // Both files should be checked by the spec, let's assume it returns both
    // for simplicity in mocking
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

    when(fileRepository.findAll(any(Specification.class))).thenReturn(foundFiles);
    when(permissionService.canRead(file1, testUser)).thenReturn(true);
    when(permissionService.canRead(file2, testUser)).thenReturn(false);

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
    when(fileRepository.findAll(any(Specification.class))).thenReturn(Collections.emptyList());

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
    assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> fileService.renameFile(fileId, newName));
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
  void listDeletedFiles_Success_FiltersByPermission() {
    setupAuthentication();
    // Given
    FileEntity deletedFileWithPermission = new FileEntity();
    deletedFileWithPermission.setId(1L);
    deletedFileWithPermission.setName("deleted1.txt");

    FileEntity deletedFileWithoutPermission = new FileEntity();
    deletedFileWithoutPermission.setId(2L);
    deletedFileWithoutPermission.setName("deleted2.txt");

    List<FileEntity> allDeletedFiles =
        Arrays.asList(deletedFileWithPermission, deletedFileWithoutPermission);

    when(fileRepository.findAllByDeletedAtIsNotNull()).thenReturn(allDeletedFiles);
    when(permissionService.canRead(deletedFileWithPermission, testUser)).thenReturn(true);
    when(permissionService.canRead(deletedFileWithoutPermission, testUser)).thenReturn(false);

    // When
    List<FileEntity> result = fileService.listDeletedFiles();

    // Then
    assertEquals(1, result.size());
    assertEquals("deleted1.txt", result.get(0).getName());
    verify(fileRepository, times(1)).findAllByDeletedAtIsNotNull();
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
    verify(fileHistoryRepository, times(1)).save(any(FileHistory.class));
    ArgumentCaptor<FileEntity> fileEntityCaptor = ArgumentCaptor.forClass(FileEntity.class);
    verify(fileRepository, times(1)).save(fileEntityCaptor.capture());
    assertNotEquals("old-key", fileEntityCaptor.getValue().getStorageKey());
    assertEquals("update.txt", fileEntityCaptor.getValue().getName());
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
    when(fileHistoryRepository.findByFileEntityIdOrderByVersionDesc(fileId)).thenReturn(Arrays.asList(history2, history1));

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
    when(fileHistoryRepository.findById(versionId)).thenReturn(Optional.of(historyToRestore));
    when(fileHistoryRepository.findByFileEntityIdOrderByVersionDesc(fileId)).thenReturn(Collections.emptyList());

    // When
    fileService.restoreFileVersion(fileId, versionId);

    // Then
    verify(fileHistoryRepository, times(1)).save(any(FileHistory.class));
    ArgumentCaptor<FileEntity> fileEntityCaptor = ArgumentCaptor.forClass(FileEntity.class);
    verify(fileRepository, times(1)).save(fileEntityCaptor.capture());
    assertEquals("restored-key", fileEntityCaptor.getValue().getStorageKey());
  }
}
