package com.example.filemanager.service;

import com.example.filemanager.controller.dto.FolderRequest;
import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.User;
import com.example.filemanager.exception.DuplicateFileException;
import com.example.filemanager.exception.ParentNotDirectoryException;
import com.example.filemanager.exception.ResourceNotFoundException;
import com.example.filemanager.repository.FileRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileService {

    private final FileRepository fileRepository;

    public FileService(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    @Transactional
    public FileEntity createDirectory(FolderRequest request) {
        User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        FileEntity parent = null;
        if (request.getParentFolderId() != null) {
            parent = fileRepository.findById(request.getParentFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent folder not found with id: " + request.getParentFolderId()));
            if (!parent.isDirectory()) {
                throw new ParentNotDirectoryException("Parent with id " + request.getParentFolderId() + " is not a directory.");
            }
        }

        fileRepository.findByParentAndName(parent, request.getName())
                .ifPresent(f -> {
                    throw new DuplicateFileException("A file or directory with the name '" + request.getName() + "' already exists in this location.");
                });

        // The primary group of the user is used as the folder's group.
        // A more sophisticated implementation might allow selecting a group.
        Group group = currentUser.getGroups().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("User does not belong to any group."));

        FileEntity newDirectory = new FileEntity();
        newDirectory.setName(request.getName());
        newDirectory.setDirectory(true);
        newDirectory.setParent(parent);
        newDirectory.setOwner(currentUser);
        newDirectory.setGroup(group);
        newDirectory.setPermissions(Integer.parseInt(request.getPermissions(), 8)); // Parse octal string

        return fileRepository.save(newDirectory);
    }
}
