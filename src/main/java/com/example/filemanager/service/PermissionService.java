package com.example.filemanager.service;

import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.Permission;
import com.example.filemanager.domain.User;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    public boolean canRead(FileEntity fileEntity, User user) {
        return isAllowed(fileEntity, user, Permission.READ);
    }

    public boolean canWrite(FileEntity fileEntity, User user) {
        return isAllowed(fileEntity, user, Permission.WRITE);
    }

    public boolean isAllowed(FileEntity fileEntity, User user, Permission requiredPermission) {
        if (fileEntity.getOwner().getId().equals(user.getId())) {
            return hasPermission(fileEntity.getPermissions() / 100, requiredPermission);
        }

        boolean inGroup = user.getGroups().stream()
                .anyMatch(g -> g.getId().equals(fileEntity.getGroup().getId()));
        if (inGroup) {
            return hasPermission((fileEntity.getPermissions() / 10) % 10, requiredPermission);
        }

        return hasPermission(fileEntity.getPermissions() % 10, requiredPermission);
    }

    private boolean hasPermission(int permissionValue, Permission requiredPermission) {
        return (permissionValue & requiredPermission.value) == requiredPermission.value;
    }
}
