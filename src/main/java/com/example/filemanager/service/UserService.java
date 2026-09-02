package com.example.filemanager.service;

import com.example.filemanager.controller.dto.UserRequest;
import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.User;
import com.example.filemanager.exception.DuplicateUsernameException;
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

    /** The account that cannot be deleted or demoted, so the system stays administrable. */
    public static final String BOOTSTRAP_ADMIN = "admin";

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
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new DuplicateUsernameException(
                    "ユーザー名 '" + user.getUsername() + "' は既に使用されています。");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (groupIds != null && !groupIds.isEmpty()) {
            user.setGroups(new HashSet<>(resolveGroups(groupIds)));
        }
        return userRepository.save(user);
    }

    public User createUser(@NonNull User user) {
        return createUser(user, null);
    }

    public User createUser(@NonNull UserRequest request) {
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("パスワードを入力してください。");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        return createUser(user, request.getGroupIds());
    }

    public User updateUser(@NonNull Long id, @NonNull UserRequest request) {
        User details = new User();
        details.setUsername(request.getUsername());
        details.setPassword(request.getPassword());
        return updateUser(id, details, request.getGroupIds());
    }

    /** Resolves group ids, failing loudly on ids that do not exist. */
    private List<Group> resolveGroups(List<Long> groupIds) {
        List<Group> groups = groupRepository.findAllById(groupIds);
        if (groups.size() != groupIds.size()) {
            throw new GroupNotFoundException("指定されたグループの一部が存在しません。");
        }
        return groups;
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

        if (BOOTSTRAP_ADMIN.equals(user.getUsername())) {
            if (!BOOTSTRAP_ADMIN.equals(userDetails.getUsername())) {
                throw new IllegalArgumentException("admin ユーザーのユーザー名は変更できません。");
            }
            if (groupIds != null) {
                Group adminsGroup = groupRepository.findByName(User.ADMIN_GROUP)
                        .orElseThrow(() -> new GroupNotFoundException("admins グループが見つかりません。"));
                if (!groupIds.contains(adminsGroup.getId())) {
                    throw new IllegalArgumentException(
                            "admin ユーザーを admins グループから外すことはできません。");
                }
            }
        }

        if (!user.getUsername().equals(userDetails.getUsername())) {
            userRepository.findByUsername(userDetails.getUsername()).ifPresent(existing -> {
                throw new DuplicateUsernameException(
                        "ユーザー名 '" + userDetails.getUsername() + "' は既に使用されています。");
            });
        }

        user.setUsername(userDetails.getUsername());
        if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDetails.getPassword()));
        }
        if (groupIds != null) {
            user.setGroups(new HashSet<>(resolveGroups(groupIds)));
        }
        return userRepository.save(user);
    }

    public User updateUser(@NonNull Long id, User userDetails) {
        return updateUser(id, userDetails, null);
    }

    public void deleteUser(@NonNull Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        if (BOOTSTRAP_ADMIN.equals(user.getUsername())) {
            throw new IllegalArgumentException("admin ユーザーは削除できません。");
        }

        // Transfer ownership of all files/folders to admin user
        User adminUser = userRepository.findByUsername(BOOTSTRAP_ADMIN)
                .orElseThrow(() -> new UserNotFoundException("admin ユーザーが見つかりません。"));

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
                .orElseThrow(() -> new UsernameNotFoundException("ユーザーが見つかりません: " + username));
    }
}
