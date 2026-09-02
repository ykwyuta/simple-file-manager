package com.example.filemanager.service;

import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Permission;
import com.example.filemanager.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Evaluates the Linux-style permission triplet stored on {@link FileEntity}.
 *
 * <p>
 * Administrators bypass the check entirely. That decision lives here and only
 * here, so every caller — listing, download, chmod, chown — agrees on who is
 * privileged.
 */
@Service
public class PermissionService {

    private static final Logger logger = LoggerFactory.getLogger(PermissionService.class);

    public boolean canRead(FileEntity fileEntity, User user) {
        return isAllowed(fileEntity, user, Permission.READ);
    }

    public boolean canWrite(FileEntity fileEntity, User user) {
        return isAllowed(fileEntity, user, Permission.WRITE);
    }

    /** True when the user belongs to the administrator group. */
    public boolean isAdmin(User user) {
        return user != null && user.isAdmin();
    }

    public boolean isAllowed(FileEntity fileEntity, User user, Permission requiredPermission) {
        if (isAdmin(user)) {
            return true;
        }

        int permissions = fileEntity.getPermissions();

        // Extract individual permission digits (e.g., 755 -> 7, 5, 5)
        int ownerPerm = permissions / 100;
        int groupPerm = (permissions / 10) % 10;
        int otherPerm = permissions % 10;

        if (fileEntity.getOwner().getId().equals(user.getId())) {
            boolean allowed = hasPermission(ownerPerm, requiredPermission);
            logger.debug("Owner check for file '{}' (permissions: {}): user={}, ownerPerm={}, required={}, allowed={}",
                    fileEntity.getName(), permissions, user.getUsername(), ownerPerm, requiredPermission, allowed);
            return allowed;
        }

        boolean inGroup = user.getGroups().stream()
                .anyMatch(g -> g.getId().equals(fileEntity.getGroup().getId()));
        if (inGroup) {
            boolean allowed = hasPermission(groupPerm, requiredPermission);
            logger.debug("Group check for file '{}' (permissions: {}): user={}, groupPerm={}, required={}, allowed={}",
                    fileEntity.getName(), permissions, user.getUsername(), groupPerm, requiredPermission, allowed);
            return allowed;
        }

        boolean allowed = hasPermission(otherPerm, requiredPermission);
        logger.debug("Others check for file '{}' (permissions: {}): user={}, otherPerm={}, required={}, allowed={}",
                fileEntity.getName(), permissions, user.getUsername(), otherPerm, requiredPermission, allowed);
        return allowed;
    }

    private boolean hasPermission(int permissionValue, Permission requiredPermission) {
        return (permissionValue & requiredPermission.value) == requiredPermission.value;
    }
}
