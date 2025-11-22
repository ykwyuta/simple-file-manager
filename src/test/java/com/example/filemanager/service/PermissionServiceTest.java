package com.example.filemanager.service;

import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.Permission;
import com.example.filemanager.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionServiceTest {

    private PermissionService permissionService;
    private User owner, groupMember, otherUser;
    private Group group;
    private FileEntity fileEntity;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService();

        owner = new User();
        owner.setId(1L);

        group = new Group();
        group.setId(1L);

        groupMember = new User();
        groupMember.setId(2L);
        groupMember.getGroups().add(group);

        otherUser = new User();
        otherUser.setId(3L);

        fileEntity = new FileEntity();
        fileEntity.setOwner(owner);
        fileEntity.setGroup(group);
    }

    @Test
    void ownerShouldHaveFullPermissions() {
        fileEntity.setPermissions(750);
        assertTrue(permissionService.isAllowed(fileEntity, owner, Permission.READ));
        assertTrue(permissionService.isAllowed(fileEntity, owner, Permission.WRITE));
        assertTrue(permissionService.isAllowed(fileEntity, owner, Permission.EXECUTE));
    }

    @Test
    void groupMemberShouldHaveGroupPermissions() {
        fileEntity.setPermissions(750);
        assertTrue(permissionService.isAllowed(fileEntity, groupMember, Permission.READ));
        assertFalse(permissionService.isAllowed(fileEntity, groupMember, Permission.WRITE));
        assertTrue(permissionService.isAllowed(fileEntity, groupMember, Permission.EXECUTE));
    }

    @Test
    void otherUserShouldHaveOtherPermissions() {
        fileEntity.setPermissions(750);
        assertFalse(permissionService.isAllowed(fileEntity, otherUser, Permission.READ));
        assertFalse(permissionService.isAllowed(fileEntity, otherUser, Permission.WRITE));
        assertFalse(permissionService.isAllowed(fileEntity, otherUser, Permission.EXECUTE));
    }

    @Test
    void permissionShouldBeDeniedWhenNotSufficient() {
        fileEntity.setPermissions(400);
        assertTrue(permissionService.isAllowed(fileEntity, owner, Permission.READ));
        assertFalse(permissionService.isAllowed(fileEntity, owner, Permission.WRITE));
        assertFalse(permissionService.isAllowed(fileEntity, owner, Permission.EXECUTE));
    }
}
