package com.example.filemanager.service;

import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.repository.FileRepository;
import io.awspring.cloud.s3.S3Template;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduledDeletionService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduledDeletionService.class);

    private final FileRepository fileRepository;
    private final S3Template s3Template;

    @Value("${S3_BUCKET_NAME}")
    private String bucketName;

    @Value("${file.deletion.retention-period-days:7}")
    private int retentionPeriodDays;

    public ScheduledDeletionService(FileRepository fileRepository, S3Template s3Template) {
        this.fileRepository = fileRepository;
        this.s3Template = s3Template;
    }

    @Scheduled(cron = "${file.deletion.cron:0 0 2 * * *}") // Defaults to 2 AM daily
    @Transactional
    public void performScheduledDeletion() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionPeriodDays);
        logger.info("Running scheduled deletion job. Deleting files soft-deleted before {}", cutoff);

        List<FileEntity> filesToDelete = fileRepository.findAllByDeletedAtBefore(cutoff);

        if (filesToDelete.isEmpty()) {
            logger.info("No files found for permanent deletion.");
            return;
        }

        logger.info("Found {} files to delete permanently.", filesToDelete.size());

        for (FileEntity file : filesToDelete) {
            // Delete from S3 only if it's a file and has a storage key
            if (!file.isDirectory() && file.getStorageKey() != null && !file.getStorageKey().isEmpty()) {
                try {
                    s3Template.deleteObject(bucketName, file.getStorageKey());
                    logger.info("Successfully deleted file '{}' from S3 with key: {}", file.getName(), file.getStorageKey());
                } catch (Exception e) {
                    logger.error("Failed to delete file '{}' (key: {}) from S3. Skipping database deletion.",
                                 file.getName(), file.getStorageKey(), e);
                    // If S3 deletion fails, we skip DB deletion to retry later
                    continue;
                }
            }
            // Delete from database
            fileRepository.delete(file);
            logger.info("Successfully deleted file metadata for '{}' (ID: {}) from database.", file.getName(), file.getId());
        }

        logger.info("Scheduled deletion job finished.");
    }
}
