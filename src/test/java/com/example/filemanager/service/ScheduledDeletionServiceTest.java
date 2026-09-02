package com.example.filemanager.service;

import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.FileHistory;
import com.example.filemanager.repository.FileHistoryRepository;
import com.example.filemanager.repository.FileRepository;
import com.example.filemanager.storage.FileStorage;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class ScheduledDeletionServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FileHistoryRepository fileHistoryRepository;

    @Mock
    private FileStorage fileStorage;

    @InjectMocks
    private ScheduledDeletionService scheduledDeletionService;

    private static final int RETENTION_DAYS = 7;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduledDeletionService, "retentionPeriodDays", RETENTION_DAYS);
    }

    private FileEntity expiredFile(long id, String name, String storageKey) {
        FileEntity file = new FileEntity();
        file.setId(id);
        file.setName(name);
        file.setDirectory(false);
        file.setStorageKey(storageKey);
        file.setDeletedAt(LocalDateTime.now().minusDays(RETENTION_DAYS + 1L));
        return file;
    }

    @Test
    void deletesExpiredFilesFromStorageAndDatabase() {
        FileEntity file = expiredFile(1L, "old_file.txt", "key-1");
        FileEntity folder = new FileEntity();
        folder.setId(2L);
        folder.setName("old_folder");
        folder.setDirectory(true);
        folder.setDeletedAt(LocalDateTime.now().minusDays(RETENTION_DAYS + 1L));

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        when(fileRepository.findAllByDeletedAtBefore(cutoff.capture()))
                .thenReturn(List.of(file, folder));
        when(fileHistoryRepository.findByFileEntityId(anyLong())).thenReturn(List.of());

        scheduledDeletionService.performScheduledDeletion();

        // Only the file has bytes in storage; the folder is metadata only.
        verify(fileStorage, times(1)).delete("key-1");
        verify(fileRepository).delete(file);
        verify(fileRepository).delete(folder);
    }

    @Test
    void deletesEveryStoredVersionOfAFile() {
        // Version rows used to be left behind, orphaning their stored objects.
        FileEntity file = expiredFile(1L, "versioned.txt", "current-key");
        FileHistory v1 = new FileHistory();
        v1.setId(10L);
        v1.setStorageKey("old-key-1");
        FileHistory v2 = new FileHistory();
        v2.setId(11L);
        v2.setStorageKey("old-key-2");

        when(fileRepository.findAllByDeletedAtBefore(any(LocalDateTime.class))).thenReturn(List.of(file));
        when(fileHistoryRepository.findByFileEntityId(1L)).thenReturn(List.of(v1, v2));

        scheduledDeletionService.performScheduledDeletion();

        verify(fileStorage).delete("old-key-1");
        verify(fileStorage).delete("old-key-2");
        verify(fileStorage).delete("current-key");
        verify(fileHistoryRepository).deleteAll(List.of(v1, v2));
        verify(fileRepository).delete(file);
    }

    @Test
    void keepsMetadataWhenStorageDeletionFails() {
        // The metadata is the only record of the orphaned object, so it stays
        // until the object is really gone and the next run can retry.
        FileEntity file = expiredFile(1L, "failing.txt", "key-fail");
        when(fileRepository.findAllByDeletedAtBefore(any(LocalDateTime.class))).thenReturn(List.of(file));
        when(fileHistoryRepository.findByFileEntityId(1L)).thenReturn(List.of());
        doThrow(new RuntimeException("storage unavailable")).when(fileStorage).delete("key-fail");

        scheduledDeletionService.performScheduledDeletion();

        verify(fileStorage).delete("key-fail");
        verify(fileRepository, never()).delete(file);
    }

    @Test
    void doesNothingWhenNothingHasExpired() {
        when(fileRepository.findAllByDeletedAtBefore(any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        scheduledDeletionService.performScheduledDeletion();

        verify(fileStorage, never()).delete(anyString());
        verify(fileRepository, never()).delete(any(FileEntity.class));
    }

    @Test
    void skipsStorageWhenTheFileHasNoStorageKey() {
        FileEntity file = expiredFile(1L, "no_key.txt", null);
        when(fileRepository.findAllByDeletedAtBefore(any(LocalDateTime.class))).thenReturn(List.of(file));
        when(fileHistoryRepository.findByFileEntityId(1L)).thenReturn(List.of());

        scheduledDeletionService.performScheduledDeletion();

        verify(fileStorage, never()).delete(anyString());
        verify(fileRepository).delete(file);
    }

    @Test
    void deletesChildrenBeforeTheirParents() {
        FileEntity parent = new FileEntity();
        parent.setId(1L);
        parent.setName("parent");
        parent.setDirectory(true);
        parent.setDeletedAt(LocalDateTime.now().minusDays(RETENTION_DAYS + 1L));

        FileEntity child = expiredFile(2L, "child.txt", "child-key");
        child.setParent(parent);

        // Deliberately parent-first, the order the repository happens to return.
        when(fileRepository.findAllByDeletedAtBefore(any(LocalDateTime.class)))
                .thenReturn(List.of(parent, child));
        when(fileHistoryRepository.findByFileEntityId(anyLong())).thenReturn(List.of());

        scheduledDeletionService.performScheduledDeletion();

        InOrder order = inOrder(fileRepository);
        order.verify(fileRepository).delete(child);
        order.verify(fileRepository).delete(parent);
    }
}
