package com.example.filemanager.repository;

import com.example.filemanager.domain.FileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long>, JpaSpecificationExecutor<FileEntity> {

    /**
     * Finds a file by its ID, only if it has not been soft-deleted.
     *
     * @param id The ID of the file.
     * @return An Optional containing the FileEntity if found and not deleted, or
     *         empty otherwise.
     */
    Optional<FileEntity> findByIdAndDeletedAtIsNull(Long id);

    /**
     * Finds a file by its parent folder and name, only if it has not been
     * soft-deleted.
     *
     * @param parent The parent folder entity.
     * @param name   The name of the file or folder.
     * @return An Optional containing the FileEntity if found and not deleted, or
     *         empty otherwise.
     */
    Optional<FileEntity> findByParentAndNameAndDeletedAtIsNull(FileEntity parent, String name);

    /**
     * Finds all files in a specific parent folder, only if they have not been
     * soft-deleted.
     *
     * @param parent The parent folder entity.
     * @return A list of FileEntity objects in the specified folder.
     */
    List<FileEntity> findAllByParentAndDeletedAtIsNull(FileEntity parent);

    /**
     * Finds all files in a specific parent folder with pagination, only if they
     * have not been
     * soft-deleted.
     *
     * @param parent   The parent folder entity.
     * @param pageable Pagination information.
     * @return A page of FileEntity objects in the specified folder.
     */
    Page<FileEntity> findAllByParentAndDeletedAtIsNull(FileEntity parent, Pageable pageable);

    /**
     * Finds all files that have been soft-deleted.
     *
     * @return A list of soft-deleted FileEntity objects.
     */
    List<FileEntity> findAllByDeletedAtIsNotNull();

    /**
     * Finds a file by its ID, only if it has been soft-deleted.
     *
     * @param id The ID of the file.
     * @return An Optional containing the FileEntity if found and deleted, or empty
     *         otherwise.
     */
    Optional<FileEntity> findByIdAndDeletedAtIsNotNull(Long id);

    /**
     * Finds all files that were soft-deleted before a specified date.
     *
     * @param dateTime The cutoff date and time.
     * @return A list of FileEntity objects soft-deleted before the given timestamp.
     */
    List<FileEntity> findAllByDeletedAtBefore(LocalDateTime dateTime);

    /**
     * Finds all files owned by a specific user (including soft-deleted ones).
     *
     * @param owner The owner user entity.
     * @return A list of FileEntity objects owned by the specified user.
     */
    List<FileEntity> findAllByOwner(com.example.filemanager.domain.User owner);
}
