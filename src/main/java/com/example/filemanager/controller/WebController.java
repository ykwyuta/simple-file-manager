package com.example.filemanager.controller;

import com.example.filemanager.controller.dto.FolderRequest;
import com.example.filemanager.controller.dto.FileHistoryResponse;
import com.example.filemanager.domain.FileHistory;
import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.User;
import com.example.filemanager.service.FileService;
import com.example.filemanager.service.GroupService;
import com.example.filemanager.service.UserService;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.stream.Collectors;
import com.example.filemanager.exception.DuplicateFileException;
import com.example.filemanager.exception.FileLockedException;
import com.example.filemanager.exception.InvalidPermissionFormatException;
import com.example.filemanager.exception.ParentNotDirectoryException;
import com.example.filemanager.exception.InvalidNameException;
import com.example.filemanager.exception.ParentDeletedException;
import com.example.filemanager.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
public class WebController {

    private static final Logger logger = LoggerFactory.getLogger(WebController.class);

    /** Folders before files, then alphabetical. */
    private static final Sort LISTING_SORT = Sort.by(
            Sort.Order.desc("isDirectory"),
            Sort.Order.asc("name"));

    private final FileService fileService;
    private final UserService userService;
    private final GroupService groupService;

    public WebController(FileService fileService, UserService userService, GroupService groupService) {
        this.fileService = fileService;
        this.userService = userService;
        this.groupService = groupService;
    }

    /**
     * Turns an exception into the message shown above the file list.
     *
     * <p>
     * Kept in one place so every action reports failures the same way; the
     * per-action try/catch ladders this replaces had drifted apart, with some
     * paths leaking raw exception text and others swallowing the reason.
     */
    private static String messageFor(Exception e) {
        if (e instanceof AccessDeniedException) {
            return "権限がありません: " + e.getMessage();
        }
        if (e instanceof DuplicateFileException || e instanceof FileLockedException
                || e instanceof InvalidPermissionFormatException || e instanceof InvalidNameException
                || e instanceof ParentNotDirectoryException || e instanceof ParentDeletedException
                || e instanceof ResourceNotFoundException) {
            return e.getMessage();
        }
        if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
            return e.getMessage();
        }
        if (e instanceof IOException) {
            return "ファイルの入出力に失敗しました。";
        }
        logger.error("Unexpected failure in web action", e);
        return "処理に失敗しました。時間をおいて再度お試しください。";
    }

    /** Runs a mutating action and reports success or failure as a flash message. */
    private void run(RedirectAttributes redirectAttributes, String successMessage, WebAction action) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("message", successMessage);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", messageFor(e));
        }
    }

    @FunctionalInterface
    private interface WebAction {
        void run() throws Exception;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String index(
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal User currentUser,
            Model model) {
        // Folders first, then by name: without an explicit order the database is
        // free to return rows in any order, so a newly created item could land
        // on an arbitrary page.
        Pageable pageable = PageRequest.of(page, size, LISTING_SORT);
        Page<FileEntity> filesPage = fileService.listFiles(folderId, pageable);

        model.addAttribute("files", filesPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", filesPage.getTotalPages());
        model.addAttribute("totalItems", filesPage.getTotalElements());
        model.addAttribute("pageSize", size);
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
        run(redirectAttributes, "ファイルをアップロードしました。", () -> {
                fileService.uploadFile(Objects.requireNonNull(file), parentFolderId, Objects.requireNonNull(permissions));
        });
        return "redirect:/" + (parentFolderId != null ? "?folderId=" + parentFolderId : "");
    }

    @PostMapping("/folders")
    public String createFolder(
            @RequestParam("name") String name,
            @RequestParam(value = "parentFolderId", required = false) Long parentFolderId,
            @RequestParam(value = "permissions", defaultValue = "755") String permissions,
            RedirectAttributes redirectAttributes) {
            FolderRequest request = new FolderRequest();
            request.setName(name);
            request.setParentFolderId(parentFolderId);
            request.setPermissions(permissions);
        run(redirectAttributes, "フォルダを作成しました。", () -> {
                fileService.createDirectory(request);
        });
        return "redirect:/" + (parentFolderId != null ? "?folderId=" + parentFolderId : "");
    }

    @PostMapping("/delete/{id}")
    public String deleteFile(
            @PathVariable Long id,
            @RequestParam(value = "currentFolderId", required = false) Long currentFolderId,
            RedirectAttributes redirectAttributes) {
        run(redirectAttributes, "ゴミ箱に移動しました。", () -> {
                fileService.softDeleteFile(Objects.requireNonNull(id));
        });
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
        run(redirectAttributes, "復元しました。", () -> {
                fileService.restoreFile(Objects.requireNonNull(id));
        });
        return "redirect:/trash";
    }

    @GetMapping("/search")
    public String search(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "tags", required = false) String tags,
            Model model) {
        // With no criteria the search used to return every readable file, which
        // reads as a result set rather than an empty form.
        boolean hasCriteria = StringUtils.hasText(query) || StringUtils.hasText(tags);
        model.addAttribute("files", hasCriteria ? fileService.searchFiles(query, tags) : List.of());
        model.addAttribute("hasCriteria", hasCriteria);
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
        run(redirectAttributes, "名前を変更しました。", () -> {
                fileService.renameFile(Objects.requireNonNull(id), Objects.requireNonNull(name));
        });
        return "redirect:/" + (currentFolderId != null ? "?folderId=" + currentFolderId : "");
    }

    @PostMapping("/folders/{id}/versioning")
    public String toggleVersioning(
            @PathVariable Long id,
            @RequestParam("enabled") boolean enabled,
            RedirectAttributes redirectAttributes) {
            String status = enabled ? "有効に" : "無効に";
        run(redirectAttributes, "バージョン管理を" + status + "しました。", () -> {
                fileService.toggleVersioning(Objects.requireNonNull(id), enabled);
        });
        return "redirect:/?folderId=" + id;
    }

    @PostMapping("/files/{id}/restore/{versionId}")
    public String restoreFileVersion(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @RequestParam(value = "currentFolderId", required = false) Long currentFolderId,
            RedirectAttributes redirectAttributes) {
        run(redirectAttributes, "バージョンを復元しました。", () -> {
                fileService.restoreFileVersion(Objects.requireNonNull(id), Objects.requireNonNull(versionId));
        });
        return "redirect:/" + (currentFolderId != null ? "?folderId=" + currentFolderId : "");
    }

    @PostMapping("/chmod/{id}")
    public String changePermissions(
            @PathVariable Long id,
            @RequestParam("permissions") String permissions,
            @RequestParam(value = "currentFolderId", required = false) Long currentFolderId,
            RedirectAttributes redirectAttributes) {
        run(redirectAttributes, "パーミッションを変更しました。", () -> {
                fileService.changePermissions(Objects.requireNonNull(id), Objects.requireNonNull(permissions));
        });
        return "redirect:/" + (currentFolderId != null ? "?folderId=" + currentFolderId : "");
    }

    @PostMapping("/move/{id}")
    public String moveFile(
            @PathVariable Long id,
            @RequestParam("destinationFolderId") Long destinationFolderId,
            @RequestParam(value = "currentFolderId", required = false) Long currentFolderId,
            RedirectAttributes redirectAttributes) {
        run(redirectAttributes, "移動しました。", () -> {
                fileService.moveFile(Objects.requireNonNull(id), Objects.requireNonNull(destinationFolderId));
        });
        return "redirect:/" + (currentFolderId != null ? "?folderId=" + currentFolderId : "");
    }

    @PostMapping("/files/{id}/lock")
    public String toggleLock(
            @PathVariable Long id,
            @RequestParam("locked") boolean locked,
            @RequestParam(value = "currentFolderId", required = false) Long currentFolderId,
            RedirectAttributes redirectAttributes) {
            String status = locked ? "ロック" : "アンロック";
        run(redirectAttributes, "ファイルを" + status + "しました。", () -> {
                fileService.updateLockStatus(Objects.requireNonNull(id), locked);
        });
        return "redirect:/" + (currentFolderId != null ? "?folderId=" + currentFolderId : "");
    }

    @GetMapping("/web/api/folders")
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

    @PostMapping("/chown/{id}")
    public String changeOwner(
            @PathVariable Long id,
            @RequestParam("ownerUserId") Long ownerUserId,
            @RequestParam("ownerGroupId") Long ownerGroupId,
            @RequestParam(value = "recursive", defaultValue = "false") boolean recursive,
            @RequestParam(value = "currentFolderId", required = false) Long currentFolderId,
            RedirectAttributes redirectAttributes) {
        run(redirectAttributes, "所有者・グループを変更しました。", () -> {
                fileService.changeOwner(Objects.requireNonNull(id), Objects.requireNonNull(ownerUserId),
                        Objects.requireNonNull(ownerGroupId), recursive);
        });
        return "redirect:/" + (currentFolderId != null ? "?folderId=" + currentFolderId : "");
    }

    @PostMapping("/tags/{id}")
    public String updateTags(
            @PathVariable Long id,
            @RequestParam("tags") String tags,
            @RequestParam(value = "currentFolderId", required = false) Long currentFolderId,
            RedirectAttributes redirectAttributes) {
        run(redirectAttributes, "タグを更新しました。", () -> {
                fileService.updateTags(Objects.requireNonNull(id), Objects.requireNonNull(tags));
        });
        return "redirect:/" + (currentFolderId != null ? "?folderId=" + currentFolderId : "");
    }

    @GetMapping("/web/api/users")
    @ResponseBody
    public List<Map<String, Object>> getUsers() {
        List<User> users = userService.findAllUsers();
        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : users) {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            result.add(userInfo);
        }
        return result;
    }

    @GetMapping("/web/api/files/{id}/versions")
    @ResponseBody
    public List<FileHistoryResponse> getFileVersions(@PathVariable Long id) {
        List<FileHistory> versions = fileService.getFileVersions(Objects.requireNonNull(id));
        return versions.stream().map(FileHistoryResponse::new).collect(Collectors.toList());
    }

    @GetMapping("/web/api/groups")
    @ResponseBody
    public List<Map<String, Object>> getGroups() {
        List<Group> groups = groupService.findAllGroups();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Group group : groups) {
            Map<String, Object> groupInfo = new HashMap<>();
            groupInfo.put("id", group.getId());
            groupInfo.put("name", group.getName());
            result.add(groupInfo);
        }
        return result;
    }

}
