package com.example.filemanager.controller;

import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.User;
import com.example.filemanager.service.GroupService;
import com.example.filemanager.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/users")
public class AdminController {

    private final UserService userService;
    private final GroupService groupService;

    public AdminController(UserService userService, GroupService groupService) {
        this.userService = userService;
        this.groupService = groupService;
    }

    @GetMapping
    public String listUsers(Model model) {
        List<User> users = userService.findAllUsers();
        List<Group> groups = groupService.findAllGroups();
        model.addAttribute("users", users);
        model.addAttribute("groups", groups);
        return "admin/users";
    }

    @PostMapping
    public String createUser(@ModelAttribute User user, @RequestParam(required = false) List<Long> groupIds,
            RedirectAttributes redirectAttributes) {
        try {
            userService.createUser(user, groupIds);
            redirectAttributes.addFlashAttribute("message", "User created successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to create user: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/update")
    public String updateUser(@PathVariable Long id, @ModelAttribute User user,
            @RequestParam(required = false) List<Long> groupIds, RedirectAttributes redirectAttributes) {
        try {
            userService.updateUser(id, user, groupIds);
            redirectAttributes.addFlashAttribute("message", "User updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to update user: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.deleteUser(id);
            redirectAttributes.addFlashAttribute("message", "User deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete user: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }
}
