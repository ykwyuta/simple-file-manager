package com.example.filemanager.service;

import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Group;
import com.example.filemanager.exception.GroupNotFoundException;
import com.example.filemanager.repository.FileRepository;
import com.example.filemanager.repository.GroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import org.springframework.lang.NonNull;

@Service
@Transactional
public class GroupService {

    private final GroupRepository groupRepository;
    private final FileRepository fileRepository;

    public GroupService(GroupRepository groupRepository, FileRepository fileRepository) {
        this.groupRepository = groupRepository;
        this.fileRepository = fileRepository;
    }

    public Group createGroup(@NonNull Group group) {
        return groupRepository.save(group);
    }

    @Transactional(readOnly = true)
    public Optional<Group> findGroupById(@NonNull Long id) {
        return groupRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Group> findAllGroups() {
        return groupRepository.findAll();
    }

    public Group updateGroup(@NonNull Long id, Group groupDetails) {
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new GroupNotFoundException("Group not found with id: " + id));
        if ("admins".equals(group.getName()) && !"admins".equals(groupDetails.getName())) {
            throw new IllegalArgumentException("Cannot rename admins group");
        }
        group.setName(groupDetails.getName());
        return groupRepository.save(group);
    }

    public void deleteGroup(@NonNull Long id) {
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new GroupNotFoundException("Group not found with id: " + id));
        if ("admins".equals(group.getName())) {
            throw new IllegalArgumentException("Cannot delete admins group");
        }

        // Transfer ownership of all files/folders to admins group
        Group adminsGroup = groupRepository.findByName("admins")
                .orElseThrow(() -> new GroupNotFoundException("Admins group not found"));

        List<FileEntity> ownedFiles = fileRepository.findAllByGroup(group);
        for (FileEntity file : ownedFiles) {
            file.setGroup(adminsGroup);
            fileRepository.save(file);
        }

        groupRepository.deleteById(id);
    }
}
