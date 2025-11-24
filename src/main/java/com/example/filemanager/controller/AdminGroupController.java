package com.example.filemanager.controller;

import com.example.filemanager.domain.Group;
import com.example.filemanager.service.GroupService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Objects;

@Controller
@RequestMapping("/admin/groups")
public class AdminGroupController {

    private final GroupService groupService;

    public AdminGroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public String listGroups(Model model) {
        List<Group> groups = groupService.findAllGroups();
        model.addAttribute("groups", groups);
        return "admin/groups";
    }

    @PostMapping
    public String createGroup(@ModelAttribute Group group, RedirectAttributes redirectAttributes) {
        try {
            groupService.createGroup(Objects.requireNonNull(group));
            redirectAttributes.addFlashAttribute("message", "Group created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create group: " + e.getMessage());
        }
        return "redirect:/admin/groups";
    }

    @PostMapping("/{id}/update")
    public String updateGroup(@PathVariable Long id, @ModelAttribute Group group,
            RedirectAttributes redirectAttributes) {
        try {
            groupService.updateGroup(Objects.requireNonNull(id), group);
            redirectAttributes.addFlashAttribute("message", "Group updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update group: " + e.getMessage());
        }
        return "redirect:/admin/groups";
    }

    @PostMapping("/{id}/delete")
    public String deleteGroup(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            groupService.deleteGroup(Objects.requireNonNull(id));
            redirectAttributes.addFlashAttribute("message", "Group deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete group: " + e.getMessage());
        }
        return "redirect:/admin/groups";
    }
}
