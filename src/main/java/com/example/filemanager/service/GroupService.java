package com.example.filemanager.service;

import com.example.filemanager.controller.dto.GroupRequest;
import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.User;
import com.example.filemanager.exception.DuplicateGroupException;
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
        groupRepository.findByName(group.getName()).ifPresent(existing -> {
            throw new DuplicateGroupException("グループ名 '" + group.getName() + "' は既に使用されています。");
        });
        return groupRepository.save(group);
    }

    public Group createGroup(@NonNull GroupRequest request) {
        Group group = new Group();
        group.setName(request.getName());
        return createGroup(group);
    }

    public Group updateGroup(@NonNull Long id, @NonNull GroupRequest request) {
        Group details = new Group();
        details.setName(request.getName());
        return updateGroup(id, details);
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
        if (User.ADMIN_GROUP.equals(group.getName()) && !User.ADMIN_GROUP.equals(groupDetails.getName())) {
            throw new IllegalArgumentException("admins グループの名前は変更できません。");
        }
        if (!group.getName().equals(groupDetails.getName())) {
            groupRepository.findByName(groupDetails.getName()).ifPresent(existing -> {
                throw new DuplicateGroupException(
                        "グループ名 '" + groupDetails.getName() + "' は既に使用されています。");
            });
        }
        group.setName(groupDetails.getName());
        return groupRepository.save(group);
    }

    public void deleteGroup(@NonNull Long id) {
        Group group = groupRepository.findById(id)
                .orElseThrow(() -> new GroupNotFoundException("Group not found with id: " + id));
        if (User.ADMIN_GROUP.equals(group.getName())) {
            throw new IllegalArgumentException("admins グループは削除できません。");
        }

        // Transfer ownership of all files/folders to admins group
        Group adminsGroup = groupRepository.findByName(User.ADMIN_GROUP)
                .orElseThrow(() -> new GroupNotFoundException("admins グループが見つかりません。"));

        List<FileEntity> ownedFiles = fileRepository.findAllByGroup(group);
        for (FileEntity file : ownedFiles) {
            file.setGroup(adminsGroup);
            fileRepository.save(file);
        }

        groupRepository.deleteById(id);
    }
}
