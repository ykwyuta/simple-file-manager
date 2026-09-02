package com.example.filemanager.service;

import com.example.filemanager.domain.Group;
import com.example.filemanager.exception.GroupNotFoundException;
import com.example.filemanager.repository.FileRepository;
import com.example.filemanager.repository.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private FileRepository fileRepository;

    @InjectMocks
    private GroupService groupService;

    private Group group;

    @BeforeEach
    void setUp() {
        group = new Group();
        group.setId(1L);
        group.setName("testgroup");
    }

    @Test
    void createGroup() {
        when(groupRepository.save(any(Group.class))).thenReturn(group);
        Group created = groupService.createGroup(new Group());
        assertEquals(group.getName(), created.getName());
        verify(groupRepository, times(1)).save(any(Group.class));
    }

    @Test
    void updateGroup_whenGroupNotFound_shouldThrowException() {
        when(groupRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(GroupNotFoundException.class, () -> groupService.updateGroup(1L, new Group()));
    }

    @Test
    void updateGroup() {
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenReturn(group);

        Group groupDetails = new Group();
        groupDetails.setName("newgroup");

        groupService.updateGroup(1L, groupDetails);

        verify(groupRepository, times(1)).save(group);
        assertEquals("newgroup", group.getName());
    }

    @Test
    void deleteGroup() {
        Group admins = new Group();
        admins.setId(99L);
        admins.setName("admins");

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(groupRepository.findByName("admins")).thenReturn(Optional.of(admins));
        when(fileRepository.findAllByGroup(group)).thenReturn(java.util.List.of());

        groupService.deleteGroup(1L);

        verify(groupRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteGroup_TransfersOwnedFilesToAdmins() {
        // Files must not be left pointing at a group that no longer exists.
        Group admins = new Group();
        admins.setId(99L);
        admins.setName("admins");

        com.example.filemanager.domain.FileEntity owned = new com.example.filemanager.domain.FileEntity();
        owned.setId(5L);
        owned.setGroup(group);

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(groupRepository.findByName("admins")).thenReturn(Optional.of(admins));
        when(fileRepository.findAllByGroup(group)).thenReturn(java.util.List.of(owned));

        groupService.deleteGroup(1L);

        assertEquals(admins, owned.getGroup());
        verify(fileRepository, times(1)).save(owned);
        verify(groupRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteGroup_RefusesToDeleteAdminsGroup() {
        Group admins = new Group();
        admins.setId(1L);
        admins.setName("admins");
        when(groupRepository.findById(1L)).thenReturn(Optional.of(admins));

        assertThrows(IllegalArgumentException.class, () -> groupService.deleteGroup(1L));
        verify(groupRepository, never()).deleteById(anyLong());
    }

    @Test
    void findAllGroups() {
        when(groupRepository.findAll()).thenReturn(java.util.List.of(group));
        var groups = groupService.findAllGroups();
        assertFalse(groups.isEmpty());
        assertEquals(1, groups.size());
        verify(groupRepository, times(1)).findAll();
    }

    @Test
    void findGroupById() {
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        var foundGroup = groupService.findGroupById(1L);
        assertTrue(foundGroup.isPresent());
        assertEquals(group.getName(), foundGroup.get().getName());
        verify(groupRepository, times(1)).findById(1L);
    }
}
