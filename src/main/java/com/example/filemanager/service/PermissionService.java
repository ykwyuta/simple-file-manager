package com.example.filemanager.service;

import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.Permission;
import com.example.filemanager.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private static final Logger logger = LoggerFactory.getLogger(PermissionService.class);

    public boolean canRead(FileEntity fileEntity, User user) {
        return isAllowed(fileEntity, user, Permission.READ);
    }

    public boolean canWrite(FileEntity fileEntity, User user) {
        return isAllowed(fileEntity, user, Permission.WRITE);
    }

    public boolean isAllowed(FileEntity fileEntity, User user, Permission requiredPermission) {
        int permissions = fileEntity.getPermissions();

        // Convert decimal to octal string to extract individual permission digits
        String octalStr = String.format("%03o", permissions);
        int ownerPerm = Character.getNumericValue(octalStr.charAt(0));
        int groupPerm = Character.getNumericValue(octalStr.charAt(1));
        int otherPerm = Character.getNumericValue(octalStr.charAt(2));

        // Check if user is owner
        if (fileEntity.getOwner().getId().equals(user.getId())) {
            boolean allowed = hasPermission(ownerPerm, requiredPermission);
            logger.debug("Owner check for file '{}' (permissions: {}): user={}, ownerPerm={}, required={}, allowed={}",
                    fileEntity.getName(), octalStr, user.getUsername(), ownerPerm, requiredPermission, allowed);
            return allowed;
        }

        // Check if user is in group
        boolean inGroup = user.getGroups().stream()
                .anyMatch(g -> g.getId().equals(fileEntity.getGroup().getId()));
        if (inGroup) {
            boolean allowed = hasPermission(groupPerm, requiredPermission);
            logger.debug("Group check for file '{}' (permissions: {}): user={}, groupPerm={}, required={}, allowed={}",
                    fileEntity.getName(), octalStr, user.getUsername(), groupPerm, requiredPermission, allowed);
            return allowed;
        }

        // Check others permission
        boolean allowed = hasPermission(otherPerm, requiredPermission);
        logger.debug("Others check for file '{}' (permissions: {}): user={}, otherPerm={}, required={}, allowed={}",
                fileEntity.getName(), octalStr, user.getUsername(), otherPerm, requiredPermission, allowed);
        return allowed;
    }

    private boolean hasPermission(int permissionValue, Permission requiredPermission) {
        return (permissionValue & requiredPermission.value) == requiredPermission.value;
    }
}
