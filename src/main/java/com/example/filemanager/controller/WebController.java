package com.example.filemanager.controller;

import com.example.filemanager.controller.dto.FolderRequest;
import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.service.FileService;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebController {

    private final FileService fileService;

    public WebController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) Long folderId, Model model) {
        List<FileEntity> files = fileService.listFiles(folderId);
        model.addAttribute("files", files);
        model.addAttribute("currentFolderId", folderId);

        if (folderId != null) {
            FileEntity currentFolder = fileService.findFileById(folderId);
            model.addAttribute("currentFolder", currentFolder);
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
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Failed to upload file: " + e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
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
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
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
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
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
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
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
}
