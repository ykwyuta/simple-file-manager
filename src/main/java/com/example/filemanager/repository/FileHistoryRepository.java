package com.example.filemanager.repository;

import com.example.filemanager.domain.FileHistory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileHistoryRepository extends JpaRepository<FileHistory, Long> {

    /**
     * Version rows with their modifier loaded.
     *
     * <p>
     * The modifier's name is rendered in the history dialog, so it has to be
     * fetched inside the transaction now that {@code open-in-view} is off.
     */
    @EntityGraph(attributePaths = { "modifier" })
    List<FileHistory> findByFileEntityIdOrderByVersionDesc(Long fileEntityId);

    /**
     * Looks a version up <em>within</em> one file.
     *
     * <p>
     * Always prefer this over {@code findById} when the version id comes from a
     * request: resolving a version without binding it to its file lets a caller
     * graft another user's stored object onto a file they own.
     */
    Optional<FileHistory> findByIdAndFileEntityId(Long id, Long fileEntityId);

    /** All history rows for a file, used when the file is hard-deleted. */
    List<FileHistory> findByFileEntityId(Long fileEntityId);
}
