package com.example.filemanager.service;

import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.repository.FileRepository;
import io.awspring.cloud.s3.S3Template;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledDeletionServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private S3Template s3Template;

    @InjectMocks
    private ScheduledDeletionService scheduledDeletionService;

    private final String BUCKET_NAME = "test-bucket";
    private final int RETENTION_DAYS = 7;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduledDeletionService, "bucketName", BUCKET_NAME);
        ReflectionTestUtils.setField(scheduledDeletionService, "retentionPeriodDays", RETENTION_DAYS);
    }

    @Test
    void performScheduledDeletion_DeletesOldFiles() {
        // Given
        FileEntity fileToDelete = new FileEntity();
        fileToDelete.setId(1L);
        fileToDelete.setName("old_file.txt");
        fileToDelete.setDirectory(false);
        fileToDelete.setStorageKey("s3-key-1");
        fileToDelete.setDeletedAt(LocalDateTime.now().minusDays(RETENTION_DAYS + 1));

        FileEntity folderToDelete = new FileEntity();
        folderToDelete.setId(2L);
        folderToDelete.setName("old_folder");
        folderToDelete.setDirectory(true);
        folderToDelete.setDeletedAt(LocalDateTime.now().minusDays(RETENTION_DAYS + 1));

        List<FileEntity> files = List.of(fileToDelete, folderToDelete);

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        when(fileRepository.findAllByDeletedAtBefore(cutoffCaptor.capture())).thenReturn(files);

        // When
        scheduledDeletionService.performScheduledDeletion();

        // Then
        // Verify S3 deletion was called for the file but not the folder
        verify(s3Template, times(1)).deleteObject(BUCKET_NAME, "s3-key-1");

        // Verify repository deletion was called for both
        verify(fileRepository, times(1)).delete(fileToDelete);
        verify(fileRepository, times(1)).delete(folderToDelete);
        verify(fileRepository, times(2)).delete(any(FileEntity.class));
    }

    @Test
    void performScheduledDeletion_S3DeleteFails_SkipsDatabaseDelete() {
        // Given
        FileEntity fileToDelete = new FileEntity();
        fileToDelete.setId(1L);
        fileToDelete.setName("failing_s3_file.txt");
        fileToDelete.setDirectory(false);
        fileToDelete.setStorageKey("s3-key-fail");
        fileToDelete.setDeletedAt(LocalDateTime.now().minusDays(RETENTION_DAYS + 1));

        when(fileRepository.findAllByDeletedAtBefore(any(LocalDateTime.class)))
            .thenReturn(List.of(fileToDelete));

        // Simulate S3 deletion failure
        doThrow(new RuntimeException("S3 is unavailable"))
            .when(s3Template).deleteObject(BUCKET_NAME, "s3-key-fail");

        // When
        scheduledDeletionService.performScheduledDeletion();

        // Then
        // Verify S3 deletion was attempted
        verify(s3Template, times(1)).deleteObject(BUCKET_NAME, "s3-key-fail");

        // IMPORTANT: Verify that the database delete was never called for this file
        verify(fileRepository, never()).delete(fileToDelete);
    }

    @Test
    void performScheduledDeletion_NoFilesToDelete() {
        // Given
        when(fileRepository.findAllByDeletedAtBefore(any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        // When
        scheduledDeletionService.performScheduledDeletion();

        // Then
        // Verify that no interactions with S3 or database deletion methods occurred
        verify(s3Template, never()).deleteObject(anyString(), anyString());
        verify(fileRepository, never()).delete(any(FileEntity.class));
    }

     @Test
    void performScheduledDeletion_FileHasNoStorageKey_SkipsS3Delete() {
        // Given
        FileEntity fileWithoutKey = new FileEntity();
        fileWithoutKey.setId(1L);
        fileWithoutKey.setName("no_key.txt");
        fileWithoutKey.setDirectory(false);
        fileWithoutKey.setStorageKey(null); // No key
        fileWithoutKey.setDeletedAt(LocalDateTime.now().minusDays(RETENTION_DAYS + 1));

        when(fileRepository.findAllByDeletedAtBefore(any(LocalDateTime.class)))
            .thenReturn(List.of(fileWithoutKey));

        // When
        scheduledDeletionService.performScheduledDeletion();

        // Then
        // Verify S3 deletion was NOT called
        verify(s3Template, never()).deleteObject(anyString(), anyString());

        // Verify repository deletion was still called
        verify(fileRepository, times(1)).delete(fileWithoutKey);
    }
}
