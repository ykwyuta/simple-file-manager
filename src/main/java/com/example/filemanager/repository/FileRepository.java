package com.example.filemanager.repository;

import com.example.filemanager.domain.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long>, JpaSpecificationExecutor<FileEntity> {

    /**
     * Finds a file by its ID, only if it has not been soft-deleted.
     *
     * @param id The ID of the file.
     * @return An Optional containing the FileEntity if found and not deleted, or empty otherwise.
     */
    Optional<FileEntity> findByIdAndDeletedAtIsNull(Long id);

    /**
     * Finds a file by its parent folder and name, only if it has not been soft-deleted.
     *
     * @param parent The parent folder entity.
     * @param name   The name of the file or folder.
     * @return An Optional containing the FileEntity if found and not deleted, or empty otherwise.
     */
    Optional<FileEntity> findByParentAndNameAndDeletedAtIsNull(FileEntity parent, String name);
}
