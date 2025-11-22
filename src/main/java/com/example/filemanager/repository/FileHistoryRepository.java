package com.example.filemanager.repository;

import com.example.filemanager.domain.FileHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FileHistoryRepository extends JpaRepository<FileHistory, Long> {
    List<FileHistory> findByFileEntityIdOrderByVersionDesc(Long fileEntityId);
}
