package com.example.filemanager.controller;

import com.example.filemanager.controller.dto.FolderRequest;
import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.User;
import com.example.filemanager.service.FileService;
import java.io.IOException;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.example.filemanager.exception.DuplicateFileException;
import com.example.filemanager.exception.FileLockedException;
import com.example.filemanager.exception.InvalidPermissionFormatException;
import com.example.filemanager.exception.ParentNotDirectoryException;
import com.example.filemanager.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class WebController {

    private final FileService fileService;

    public WebController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/")
    public String index(
            @RequestParam(required = false) Long folderId,
            @AuthenticationPrincipal User currentUser,
            Model model) {
        List<FileEntity> files = fileService.listFiles(folderId);
        model.addAttribute("files", files);
        model.addAttribute("currentFolderId", folderId);
        model.addAttribute("currentUser", currentUser);

        if (folderId != null) {
            FileEntity currentFolder = fileService.findFileById(folderId);
            model.addAttribute("currentFolder", currentFolder);
            model.addAttribute("breadcrumbs", fileService.getBreadcrumbs(folderId));
            if (currentFolder.getParent() != null) {
                model.addAttribute("parentFolderId", currentFolder.getParent().getId());
            }
        }

        return "home";
    }

    @PostMapping("/upload")
    public String uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parentFolderId", required = false) Long parentFolderId,
            @RequestParam(value = "permissions", defaultValue = "644") String permissions,
            RedirectAttributes redirectAttributes) {
        try {
            fileService.uploadFile(file, parentFolderId, permissions);
            redirectAttributes.addFlashAttribute("message", "File uploaded successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error",
                    "Permission denied: You don't have write access to this folder.");
        } catch (DuplicateFileException e) {
            redirectAttributes.addFlashAttribute("error", "Duplicate file: " + e.getMessage());
        } catch (InvalidPermissionFormatException e) {
            redirectAttributes.addFlashAttribute("error", "Invalid permissions: " + e.getMessage());
        } catch (ParentNotDirectoryException e) {
            redirectAttributes.addFlashAttribute("error", "Invalid parent folder: " + e.getMessage());
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Failed to upload file: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Upload failed: " + e.getMessage());
        }
        return "redirect:/" + (parentFolderId != null ? "?folderId=" + parentFolderId : "");
    }

    @PostMapping("/folders")
    public String createFolder(
            @RequestParam("name") String name,
            @RequestParam(value = "parentFolderId", required = false) Long parentFolderId,
            @RequestParam(value = "permissions", defaultValue = "755") String permissions,
            RedirectAttributes redirectAttributes) {
        try {
            FolderRequest request = new FolderRequest();
            request.setName(name);
            request.setParentFolderId(parentFolderId);
            request.setPermissions(permissions);
            fileService.createDirectory(request);
            redirectAttributes.addFlashAttribute("message", "Folder created successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "Permission denied: " + e.getMessage());
        } catch (DuplicateFileException e) {
            redirectAttributes.addFlashAttribute("error", "Duplicate folder: " + e.getMessage());
        } catch (InvalidPermissionFormatException e) {
            redirectAttributes.addFlashAttribute("error", "Invalid permissions: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create folder: " + e.getMessage());
        }
        return "redirect:/" + (parentFolderId != null ? "?folderId=" + parentFolderId : "");
    }

    @PostMapping("/delete/{id}")
    public String deleteFile(
            @PathVariable Long id,
            @RequestParam(value = "currentFolderId", required = false) Long currentFolderId,
            RedirectAttributes redirectAttributes) {
        try {
            fileService.softDeleteFile(id);
            redirectAttributes.addFlashAttribute("message", "File deleted successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error",
                    "Permission denied: You don't have write access to delete this file.");
        } catch (FileLockedException e) {
            redirectAttributes.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", "File not found: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete: " + e.getMessage());
        }
        return "redirect:/" + (currentFolderId != null ? "?folderId=" + currentFolderId : "");
    }

    @GetMapping("/trash")
    public String trash(Model model) {
        List<FileEntity> deletedFiles = fileService.listDeletedFiles();
        model.addAttribute("files", deletedFiles);
        return "trash";
    }

    @PostMapping("/restore/{id}")
    public String restoreFile(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            fileService.restoreFile(id);
            redirectAttributes.addFlashAttribute("message", "File restored successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "Permission denied: " + e.getMessage());
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", "File not found: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to restore: " + e.getMessage());
        }
        return "redirect:/trash";
    }

    @GetMapping("/search")
    public String search(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "tags", required = false) String tags,
            Model model) {
        List<FileEntity> files = fileService.searchFiles(query, tags);
        model.addAttribute("files", files);
        model.addAttribute("query", query);
        model.addAttribute("tags", tags);
        return "search";
    }

    @PostMapping("/rename/{id}")
    public String renameFile(
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam(value = "currentFolderId", required = false) Long currentFolderId,
            RedirectAttributes redirectAttributes) {
        try {
            fileService.renameFile(id, name);
            redirectAttributes.addFlashAttribute("message", "File renamed successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error",
                    "Permission denied: You don't have write access to rename this file.");
        } catch (DuplicateFileException e) {
            redirectAttributes.addFlashAttribute("error", "Duplicate name: " + e.getMessage());
        } catch (FileLockedException e) {
            redirectAttributes.addFlashAttribute("error", "Cannot rename: " + e.getMessage());
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", "File not found: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Rename failed: " + e.getMessage());
        }
        return "redirect:/" + (currentFolderId != null ? "?folderId=" + currentFolderId : "");
    }

    @PostMapping("/folders/{id}/versioning")
    public String toggleVersioning(
            @PathVariable Long id,
            @RequestParam("enabled") boolean enabled,
            RedirectAttributes redirectAttributes) {
        try {
            fileService.toggleVersioning(id, enabled);
            String status = enabled ? "enabled" : "disabled";
            redirectAttributes.addFlashAttribute("message", "Versioning " + status + " successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error",
                    "Permission denied: You don't have write access to this folder.");
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", "Folder not found: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", "Invalid operation: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to toggle versioning: " + e.getMessage());
        }
        return "redirect:/?folderId=" + id;
    }

    @PostMapping("/files/{id}/restore/{versionId}")
    public String restoreFileVersion(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @RequestParam(value = "currentFolderId", required = false) Long currentFolderId,
            RedirectAttributes redirectAttributes) {
        try {
            fileService.restoreFileVersion(id, versionId);
            redirectAttributes.addFlashAttribute("message", "File version restored successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/" + (currentFolderId != null ? "?folderId=" + currentFolderId : "");
    }

    @PostMapping("/chmod/{id}")
    public String changePermissions(
            @PathVariable Long id,
            @RequestParam("permissions") String permissions,
            @RequestParam(value = "currentFolderId", required = false) Long currentFolderId,
            RedirectAttributes redirectAttributes) {
        try {
            fileService.changePermissions(id, permissions);
            redirectAttributes.addFlashAttribute("message", "Permissions changed successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "Permission denied: Only the owner can change permissions.");
        } catch (InvalidPermissionFormatException e) {
            redirectAttributes.addFlashAttribute("error", "Invalid permissions: " + e.getMessage());
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", "File not found: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to change permissions: " + e.getMessage());
        }
        return "redirect:/" + (currentFolderId != null ? "?folderId=" + currentFolderId : "");
    }

    @PostMapping("/move/{id}")
    public String moveFile(
            @PathVariable Long id,
            @RequestParam("destinationFolderId") Long destinationFolderId,
            @RequestParam(value = "currentFolderId", required = false) Long currentFolderId,
            RedirectAttributes redirectAttributes) {
        try {
            fileService.moveFile(id, destinationFolderId);
            redirectAttributes.addFlashAttribute("message", "File moved successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "Permission denied: " + e.getMessage());
        } catch (DuplicateFileException e) {
            redirectAttributes.addFlashAttribute("error", "Duplicate file in destination: " + e.getMessage());
        } catch (FileLockedException e) {
            redirectAttributes.addFlashAttribute("error", "Cannot move: " + e.getMessage());
        } catch (ParentNotDirectoryException e) {
            redirectAttributes.addFlashAttribute("error", "Invalid destination: " + e.getMessage());
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", "File or folder not found: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to move: " + e.getMessage());
        }
        return "redirect:/" + (currentFolderId != null ? "?folderId=" + currentFolderId : "");
    }

    @PostMapping("/files/{id}/lock")
    public String toggleLock(
            @PathVariable Long id,
            @RequestParam("locked") boolean locked,
            @AuthenticationPrincipal User currentUser,
            @RequestParam(value = "currentFolderId", required = false) Long currentFolderId,
            RedirectAttributes redirectAttributes) {
        try {
            fileService.updateLockStatus(id, locked, currentUser.getUsername());
            String status = locked ? "locked" : "unlocked";
            redirectAttributes.addFlashAttribute("message", "File " + status + " successfully!");
        } catch (AccessDeniedException e) {
            redirectAttributes.addFlashAttribute("error", "Permission denied: " + e.getMessage());
        } catch (FileLockedException e) {
            redirectAttributes.addFlashAttribute("error", "Lock error: " + e.getMessage());
        } catch (ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("error", "File not found: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update lock status: " + e.getMessage());
        }
        return "redirect:/" + (currentFolderId != null ? "?folderId=" + currentFolderId : "");
    }

    @GetMapping("/api/folders")
    @ResponseBody
    public List<Map<String, Object>> getFolders() {
        List<FileEntity> allFiles = fileService.listFiles(null);
        return getAllFoldersRecursive(allFiles);
    }

    private List<Map<String, Object>> getAllFoldersRecursive(List<FileEntity> files) {
        List<Map<String, Object>> folders = new ArrayList<>();
        for (FileEntity file : files) {
            if (file.isDirectory()) {
                Map<String, Object> folderInfo = new HashMap<>();
                folderInfo.put("id", file.getId());
                folderInfo.put("name", file.getName());
                folderInfo.put("path", getFullPath(file));
                folders.add(folderInfo);

                // Get subfolders
                List<FileEntity> subFiles = fileService.listFiles(file.getId());
                folders.addAll(getAllFoldersRecursive(subFiles));
            }
        }
        return folders;
    }

    private String getFullPath(FileEntity file) {
        if (file.getParent() == null) {
            return "/" + file.getName();
        }
        return getFullPath(file.getParent()) + "/" + file.getName();
    }

}
