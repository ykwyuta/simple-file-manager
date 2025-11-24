package com.example.filemanager.service;

import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.User;
import com.example.filemanager.exception.GroupNotFoundException;
import com.example.filemanager.exception.UserNotFoundException;
import com.example.filemanager.repository.FileRepository;
import com.example.filemanager.repository.GroupRepository;
import com.example.filemanager.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.NonNull;

@Service
@Transactional
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileRepository fileRepository;

    public UserService(UserRepository userRepository, GroupRepository groupRepository,
            PasswordEncoder passwordEncoder, FileRepository fileRepository) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileRepository = fileRepository;
    }

    public User createUser(@NonNull User user, List<Long> groupIds) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (groupIds != null && !groupIds.isEmpty()) {
            List<Group> groups = groupRepository.findAllById(groupIds);
            user.setGroups(new HashSet<>(groups));
        }
        return userRepository.save(user);
    }

    public User createUser(@NonNull User user) {
        return createUser(user, null);
    }

    @Transactional(readOnly = true)
    public Optional<User> findUserById(@NonNull Long id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    public User updateUser(@NonNull Long id, User userDetails, List<Long> groupIds) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        if ("admin".equals(user.getUsername())) {
            if (!"admin".equals(userDetails.getUsername())) {
                throw new IllegalArgumentException("Cannot change username of admin user");
            }
            if (groupIds != null) {
                Group adminsGroup = groupRepository.findByName("admins")
                        .orElseThrow(() -> new GroupNotFoundException("Admins group not found"));
                if (groupIds.size() != 1 || !groupIds.contains(adminsGroup.getId())) {
                    throw new IllegalArgumentException("Admin user must belong to and only to 'admins' group");
                }
            }
        }

        user.setUsername(userDetails.getUsername());
        if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDetails.getPassword()));
        }
        if (groupIds != null) {
            List<Group> groups = groupRepository.findAllById(groupIds);
            user.setGroups(new HashSet<>(groups));
        }
        return userRepository.save(user);
    }

    public User updateUser(@NonNull Long id, User userDetails) {
        return updateUser(id, userDetails, null);
    }

    public void deleteUser(@NonNull Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        if ("admin".equals(user.getUsername())) {
            throw new IllegalArgumentException("Cannot delete admin user");
        }

        // Transfer ownership of all files/folders to admin user
        User adminUser = userRepository.findByUsername("admin")
                .orElseThrow(() -> new UserNotFoundException("Admin user not found"));

        List<FileEntity> ownedFiles = fileRepository.findAllByOwner(user);
        for (FileEntity file : ownedFiles) {
            file.setOwner(adminUser);
            fileRepository.save(file);
        }

        userRepository.deleteById(id);
    }

    public void addUserToGroup(@NonNull Long userId, @NonNull Long groupId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found with id: " + groupId));
        user.getGroups().add(group);
        userRepository.save(user);
    }

    public void removeUserFromGroup(@NonNull Long userId, @NonNull Long groupId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException("Group not found with id: " + groupId));
        user.getGroups().remove(group);
        userRepository.save(user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
    }
}
