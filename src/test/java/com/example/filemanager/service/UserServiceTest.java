package com.example.filemanager.service;

import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.User;
import com.example.filemanager.repository.FileRepository;
import com.example.filemanager.repository.GroupRepository;
import com.example.filemanager.exception.UserNotFoundException;
import com.example.filemanager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private FileRepository fileRepository;

    private UserService userService;

    private User user;
    private Group group;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, groupRepository, passwordEncoder, fileRepository);

        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("password");

        group = new Group();
        group.setId(1L);
        group.setName("testgroup");
    }

    @Test
    void createUser() {
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        User newUser = new User();
        newUser.setPassword("password");
        User created = userService.createUser(newUser);

        assertEquals(user.getUsername(), created.getUsername());
        verify(passwordEncoder, times(1)).encode("password");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void updateUser_whenUserNotFound_shouldThrowException() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> userService.updateUser(1L, new User()));
    }

    @Test
    void updateUser_withPasswordChange() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("newHashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        User userDetails = new User();
        userDetails.setUsername("newuser");
        userDetails.setPassword("newpassword");

        userService.updateUser(1L, userDetails);

        verify(passwordEncoder, times(1)).encode("newpassword");
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void updateUser_withoutPasswordChange() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        User userDetails = new User();
        userDetails.setUsername("newuser");
        userDetails.setPassword(""); // Empty password

        userService.updateUser(1L, userDetails);

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void addUserToGroup_whenUserNotFound_shouldThrowException() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> userService.addUserToGroup(1L, 1L));
    }

    @Test
    void addUserToGroup() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));

        userService.addUserToGroup(1L, 1L);

        assertTrue(user.getGroups().contains(group));
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void removeUserFromGroup() {
        user.getGroups().add(group);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));

        userService.removeUserFromGroup(1L, 1L);

        assertFalse(user.getGroups().contains(group));
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void deleteUser_shouldTransferOwnershipToAdmin() {
        // Setup
        User adminUser = new User();
        adminUser.setId(999L);
        adminUser.setUsername("admin");

        FileEntity file1 = new FileEntity();
        file1.setId(1L);
        file1.setName("file1.txt");
        file1.setOwner(user);

        FileEntity file2 = new FileEntity();
        file2.setId(2L);
        file2.setName("file2.txt");
        file2.setOwner(user);

        List<FileEntity> ownedFiles = List.of(file1, file2);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(fileRepository.findAllByOwner(user)).thenReturn(ownedFiles);

        // Execute
        userService.deleteUser(1L);

        // Verify
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).findByUsername("admin");
        verify(fileRepository, times(1)).findAllByOwner(user);
        verify(fileRepository, times(2)).save(any(FileEntity.class));
        verify(userRepository, times(1)).deleteById(1L);

        // Verify ownership was transferred
        assertEquals(adminUser, file1.getOwner());
        assertEquals(adminUser, file2.getOwner());
    }

    @Test
    void deleteUser_whenAdminUser_shouldThrowException() {
        User adminUser = new User();
        adminUser.setId(999L);
        adminUser.setUsername("admin");

        when(userRepository.findById(999L)).thenReturn(Optional.of(adminUser));

        assertThrows(IllegalArgumentException.class, () -> userService.deleteUser(999L));
        verify(userRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteUser_whenUserNotFound_shouldThrowException() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.deleteUser(1L));
        verify(userRepository, never()).deleteById(anyLong());
    }

    @Test
    void findAllUsers() {
        when(userRepository.findAll()).thenReturn(java.util.List.of(user));
        var users = userService.findAllUsers();
        assertFalse(users.isEmpty());
        assertEquals(1, users.size());
        verify(userRepository, times(1)).findAll();
    }

    @Test
    void findUserById() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        var foundUser = userService.findUserById(1L);
        assertTrue(foundUser.isPresent());
        assertEquals(user.getUsername(), foundUser.get().getUsername());
        verify(userRepository, times(1)).findById(1L);
    }
}
