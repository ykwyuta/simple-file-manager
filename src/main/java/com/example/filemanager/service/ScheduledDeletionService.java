package com.example.filemanager.service;

import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.FileHistory;
import com.example.filemanager.repository.FileHistoryRepository;
import com.example.filemanager.repository.FileRepository;
import com.example.filemanager.storage.FileStorage;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;

@Service
public class ScheduledDeletionService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledDeletionService.class);

    private final FileRepository fileRepository;
    private final FileHistoryRepository fileHistoryRepository;
    private final FileStorage fileStorage;

    @Value("${file.deletion.retention-period-days:7}")
    private int retentionPeriodDays;

    public ScheduledDeletionService(FileRepository fileRepository,
            FileHistoryRepository fileHistoryRepository, FileStorage fileStorage) {
        this.fileRepository = fileRepository;
        this.fileHistoryRepository = fileHistoryRepository;
        this.fileStorage = fileStorage;
    }

    @Scheduled(cron = "${file.deletion.cron:0 0 2 * * *}") // Defaults to 2 AM daily
    @Transactional
    public void performScheduledDeletion() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionPeriodDays);
        logger.info("Running scheduled deletion job. Deleting files soft-deleted before {}", cutoff);

        List<FileEntity> filesToDelete = new java.util.ArrayList<>(
                fileRepository.findAllByDeletedAtBefore(cutoff));

        if (filesToDelete.isEmpty()) {
            logger.info("No files found for permanent deletion.");
            return;
        }

        logger.info("Found {} files to delete permanently.", filesToDelete.size());

        int deleted = 0;
        int failed = 0;
        // Children are hard-deleted before their parents so a failure part-way
        // through never leaves a row pointing at a missing parent.
        filesToDelete.sort(Comparator.comparingInt(ScheduledDeletionService::depthOf).reversed());

        for (FileEntity file : filesToDelete) {
            List<FileHistory> history = fileHistoryRepository.findByFileEntityId(file.getId());
            try {
                // Every stored object for this file, current and historical.
                // Skipping the history rows used to leave their S3 objects
                // orphaned forever.
                for (FileHistory version : history) {
                    deleteObject(version.getStorageKey());
                }
                if (!file.isDirectory()) {
                    deleteObject(file.getStorageKey());
                }
            } catch (Exception e) {
                logger.error("Failed to delete stored objects for '{}' (ID: {}). "
                        + "Leaving the metadata in place so the next run retries.",
                        file.getName(), file.getId(), e);
                failed++;
                continue;
            }

            fileHistoryRepository.deleteAll(history);
            fileRepository.delete(file);
            deleted++;
            logger.debug("Hard-deleted '{}' (ID: {}) and {} version(s)", file.getName(), file.getId(),
                    history.size());
        }

        logger.info("Scheduled deletion job finished. deleted={}, retryNextRun={}", deleted, failed);
    }

    private void deleteObject(String storageKey) {
        if (storageKey == null || storageKey.isEmpty()) {
            return;
        }
        fileStorage.delete(storageKey);
    }

    /** Depth in the folder tree, used to delete leaves before their parents. */
    private static int depthOf(FileEntity file) {
        int depth = 0;
        for (FileEntity p = file.getParent(); p != null; p = p.getParent()) {
            depth++;
        }
        return depth;
    }
}
