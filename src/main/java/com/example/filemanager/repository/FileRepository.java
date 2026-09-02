package com.example.filemanager.repository;

import com.example.filemanager.domain.FileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long>, JpaSpecificationExecutor<FileEntity> {

    /**
     * Association fetched eagerly for listings.
     *
     * <p>
     * The file list renders the owner, the owning group and the lock holder for
     * every row. With {@code open-in-view} disabled those associations must be
     * loaded inside the service transaction, and fetching them together also
     * removes the N+1 the listing used to issue per row.
     */
    String[] LISTING_GRAPH = { "owner", "group", "parent", "lockedBy" };

    @Override
    @EntityGraph(attributePaths = { "owner", "group", "parent", "lockedBy" })
    Page<FileEntity> findAll(Specification<FileEntity> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = { "owner", "group", "parent", "lockedBy" })
    List<FileEntity> findAll(Specification<FileEntity> spec);

    @Override
    @EntityGraph(attributePaths = { "owner", "group", "parent", "lockedBy" })
    List<FileEntity> findAll(Specification<FileEntity> spec, Sort sort);

    @Override
    @EntityGraph(attributePaths = { "owner", "group", "parent", "lockedBy" })
    Optional<FileEntity> findById(Long id);


    /**
     * Finds a file by its ID, only if it has not been soft-deleted.
     *
     * @param id The ID of the file.
     * @return An Optional containing the FileEntity if found and not deleted, or
     *         empty otherwise.
     */
    @EntityGraph(attributePaths = { "owner", "group", "parent", "lockedBy" })
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

    /**
     * Finds all files owned by a specific group (including soft-deleted ones).
     *
     * @param group The owner group entity.
     * @return A list of FileEntity objects owned by the specified group.
     */
    List<FileEntity> findAllByGroup(com.example.filemanager.domain.Group group);

    /**
     * Finds all direct children of a folder regardless of their deletion state.
     * Used when a folder is soft-deleted or restored so the whole subtree moves
     * together.
     *
     * @param parent The parent folder entity.
     * @return Every direct child, deleted or not.
     */
    List<FileEntity> findAllByParent(FileEntity parent);

    /**
     * Finds children that were soft-deleted as part of the same cascade as their
     * parent, i.e. carrying exactly the parent's deletion timestamp.
     *
     * @param parent    The parent folder entity.
     * @param deletedAt The parent's deletion timestamp.
     * @return The children deleted together with the parent.
     */
    List<FileEntity> findAllByParentAndDeletedAt(FileEntity parent, LocalDateTime deletedAt);
}
