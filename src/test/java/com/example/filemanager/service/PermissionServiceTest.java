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
    private User owner, groupMember, otherUser, administrator;
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

        Group adminsGroup = new Group();
        adminsGroup.setId(99L);
        adminsGroup.setName(User.ADMIN_GROUP);
        administrator = new User();
        administrator.setId(4L);
        administrator.getGroups().add(adminsGroup);

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
    void administratorBypassesThePermissionTriplet() {
        // Without an override an administrator could not read, move or clean up
        // anything owned by someone else -- they could not administer the system.
        fileEntity.setPermissions(700);
        assertTrue(permissionService.isAdmin(administrator));
        assertTrue(permissionService.canRead(fileEntity, administrator));
        assertTrue(permissionService.canWrite(fileEntity, administrator));
        assertTrue(permissionService.isAllowed(fileEntity, administrator, Permission.EXECUTE));
    }

    @Test
    void nonAdministratorsAreNotTreatedAsAdministrators() {
        assertFalse(permissionService.isAdmin(owner));
        assertFalse(permissionService.isAdmin(groupMember));
        assertFalse(permissionService.isAdmin(null));
    }

    @Test
    void ownerDigitWinsOverGroupDigitForTheOwner() {
        // 077: the owner has nothing, everyone else has everything. The owner
        // must still be denied -- precedence is owner, then group, then others.
        fileEntity.setPermissions(77);
        assertFalse(permissionService.canRead(fileEntity, owner));
        assertTrue(permissionService.canRead(fileEntity, groupMember));
        assertTrue(permissionService.canRead(fileEntity, otherUser));
    }

    @Test
    void groupDigitWinsOverOthersDigitForGroupMembers() {
        // 707: group members are denied even though "others" would allow it.
        fileEntity.setPermissions(707);
        assertTrue(permissionService.canRead(fileEntity, owner));
        assertFalse(permissionService.canRead(fileEntity, groupMember));
        assertTrue(permissionService.canRead(fileEntity, otherUser));
    }

    @Test
    void permissionShouldBeDeniedWhenNotSufficient() {
        fileEntity.setPermissions(400);
        assertTrue(permissionService.isAllowed(fileEntity, owner, Permission.READ));
        assertFalse(permissionService.isAllowed(fileEntity, owner, Permission.WRITE));
        assertFalse(permissionService.isAllowed(fileEntity, owner, Permission.EXECUTE));
    }
}
