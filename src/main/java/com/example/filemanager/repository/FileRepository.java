package com.example.filemanager.repository;

import com.example.filemanager.domain.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long> {

    /**
     * Finds a file by its parent folder and name.
     *
     * @param parent The parent folder entity.
     * @param name   The name of the file or folder.
     * @return An Optional containing the FileEntity if found, or empty otherwise.
     */
    Optional<FileEntity> findByParentAndName(FileEntity parent, String name);
}
