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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.mockito.Mockito.mock;


@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

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

        // Mock the SecurityContext
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(testUser);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void createDirectory_Success_RootFolder() {
        // Given
        FolderRequest request = new FolderRequest();
        request.setName("Documents");
        request.setParentFolderId(null);
        request.setPermissions("755");

        when(fileRepository.findByParentAndName(null, "Documents")).thenReturn(Optional.empty());
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
        // Given
        FileEntity parentFolder = new FileEntity();
        parentFolder.setId(1L);
        parentFolder.setName("Root");
        parentFolder.setDirectory(true);

        FolderRequest request = new FolderRequest();
        request.setName("Images");
        request.setParentFolderId(1L);
        request.setPermissions("750");

        when(fileRepository.findById(1L)).thenReturn(Optional.of(parentFolder));
        when(fileRepository.findByParentAndName(parentFolder, "Images")).thenReturn(Optional.empty());
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
        // Given
        FolderRequest request = new FolderRequest();
        request.setName("Music");
        request.setParentFolderId(99L);
        request.setPermissions("700");

        when(fileRepository.findById(99L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> fileService.createDirectory(request));
        verify(fileRepository, never()).save(any());
    }

    @Test
    void createDirectory_Failure_DuplicateName() {
        // Given
        FileEntity existingFile = new FileEntity();
        existingFile.setName("Documents");

        FolderRequest request = new FolderRequest();
        request.setName("Documents");
        request.setParentFolderId(null);
        request.setPermissions("755");

        when(fileRepository.findByParentAndName(null, "Documents")).thenReturn(Optional.of(existingFile));

        // When & Then
        assertThrows(DuplicateFileException.class, () -> fileService.createDirectory(request));
        verify(fileRepository, never()).save(any());
    }
}
