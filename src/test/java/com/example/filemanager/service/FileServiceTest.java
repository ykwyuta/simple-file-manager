package com.example.filemanager.service;

import com.example.filemanager.controller.dto.FolderRequest;
import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.User;
import com.example.filemanager.exception.DuplicateFileException;
import com.example.filemanager.exception.ResourceNotFoundException;
import com.example.filemanager.repository.FileRepository;
import com.example.filemanager.repository.GroupRepository;
import com.example.filemanager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.mockito.Mockito.mock;


@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private S3Template s3Template;

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

        when(fileRepository.findByParentAndNameAndDeletedAtIsNull(null, "Documents")).thenReturn(Optional.empty());
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> {
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
        when(fileRepository.findByParentAndNameAndDeletedAtIsNull(parentFolder, "Images")).thenReturn(Optional.empty());
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

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

        when(fileRepository.findByParentAndNameAndDeletedAtIsNull(null, "Documents")).thenReturn(Optional.of(existingFile));

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

        when(fileRepository.findByParentAndNameAndDeletedAtIsNull(null, "test.txt")).thenReturn(Optional.empty());
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        verify(s3Template, times(1)).upload(bucketNameCaptor.capture(), keyCaptor.capture(), any(java.io.InputStream.class));
        assertEquals("test-bucket", bucketNameCaptor.getValue());
        assertTrue(keyCaptor.getValue().endsWith("/test.txt"));
    }

    @Test
    void uploadFile_Failure_ParentNotFound() {
        setupAuthentication();
        // Given
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test data".getBytes());
        Long parentFolderId = 99L;
        String permissions = "644";

        when(fileRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> fileService.uploadFile(file, parentFolderId, permissions));
        verify(fileRepository, never()).save(any());
        verify(s3Template, never()).upload(anyString(), anyString(), any());
    }

    @Test
    void uploadFile_Failure_DuplicateName() {
        setupAuthentication();
        // Given
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test data".getBytes());
        Long parentFolderId = null;
        String permissions = "644";

        when(fileRepository.findByParentAndNameAndDeletedAtIsNull(null, "test.txt")).thenReturn(Optional.of(new FileEntity()));

        // When & Then
        assertThrows(DuplicateFileException.class, () -> fileService.uploadFile(file, parentFolderId, permissions));
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
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test data".getBytes());
        Long parentFolderId = null;
        String permissions = "invalid"; // Not an octal string

        // When & Then
        assertThrows(com.example.filemanager.exception.InvalidPermissionFormatException.class, () -> fileService.uploadFile(file, parentFolderId, permissions));
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
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> fileService.findFileById(1L));
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
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> fileService.softDeleteFile(1L));
        verify(fileRepository, never()).save(any());
    }
}
