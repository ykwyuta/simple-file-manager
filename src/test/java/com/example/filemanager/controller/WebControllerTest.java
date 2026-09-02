package com.example.filemanager.controller;

import com.example.filemanager.controller.dto.FolderRequest;
import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.domain.Group;
import com.example.filemanager.domain.User;
import com.example.filemanager.service.FileService;
import com.example.filemanager.service.GroupService;
import com.example.filemanager.service.UserService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for the Thymeleaf controller: routing, model wiring and the
 * success/failure flash messages.
 *
 * <p>
 * The principal is the application's own {@link User}, not
 * {@code @WithMockUser}'s: the controller and templates read {@code id} and
 * {@code username} off it.
 *
 * <p>
 * Behaviour that depends on the security filter chain (CSRF, form login,
 * authorization) is covered end-to-end in {@code e2e/}, against a running
 * application rather than a mocked one.
 */
@WebMvcTest(WebController.class)
@SuppressWarnings("null")
class WebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileService fileService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private GroupService groupService;

    private User principal;

    @BeforeEach
    void setUp() {
        Group group = new Group();
        group.setId(1L);
        group.setName("users");

        principal = new User();
        principal.setId(1L);
        principal.setUsername("tester");
        principal.setPassword("irrelevant");
        principal.setGroups(Set.of(group));
    }

    /** A file owned by the principal, complete enough for the list template. */
    private FileEntity ownedFile(long id, String name) {
        FileEntity file = new FileEntity();
        file.setId(id);
        file.setName(name);
        file.setDirectory(false);
        file.setPermissions(644);
        file.setOwner(principal);
        file.setGroup(principal.getGroups().iterator().next());
        return file;
    }

    @Test
    void showsTheFilesInTheCurrentFolder() throws Exception {
        FileEntity file = ownedFile(1L, "file1.txt");
        Page<FileEntity> page = new PageImpl<>(List.of(file));
        when(fileService.listFiles(isNull(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(model().attribute("files", List.of(file)))
                .andExpect(model().attribute("totalItems", 1L))
                .andExpect(model().attribute("currentUser", principal));
    }

    @Test
    void showsBreadcrumbsWhenInsideAFolder() throws Exception {
        FileEntity folder = new FileEntity();
        folder.setId(10L);
        folder.setName("Docs");
        folder.setDirectory(true);
        folder.setPermissions(755);
        folder.setOwner(principal);
        folder.setGroup(principal.getGroups().iterator().next());

        when(fileService.listFiles(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ownedFile(2L, "doc.txt"))));
        when(fileService.findFileById(10L)).thenReturn(folder);
        when(fileService.getBreadcrumbs(10L)).thenReturn(List.of(folder));

        mockMvc.perform(get("/").param("folderId", "10").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("currentFolder", folder))
                .andExpect(model().attribute("breadcrumbs", List.of(folder)));
    }

    @Test
    void createsAFolderAndReportsSuccess() throws Exception {
        mockMvc.perform(post("/folders")
                .param("name", "New Folder")
                .param("permissions", "755")
                .with(user(principal))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("message", "フォルダを作成しました。"));

        verify(fileService).createDirectory(any(FolderRequest.class));
    }

    @Test
    void reportsPermissionFailuresAsAMessageRatherThanAnErrorPage() throws Exception {
        doThrow(new AccessDeniedException("このフォルダにフォルダを作成する権限がありません。"))
                .when(fileService).createDirectory(any(FolderRequest.class));

        mockMvc.perform(post("/folders")
                .param("name", "New Folder")
                .with(user(principal))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    void uploadsAFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "content".getBytes());

        mockMvc.perform(multipart("/upload").file(file)
                .param("permissions", "644")
                .with(user(principal))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "ファイルをアップロードしました。"));

        verify(fileService).uploadFile(any(), isNull(), eq("644"));
    }

    @Test
    void deletesAFileAndReturnsToTheCurrentFolder() throws Exception {
        mockMvc.perform(post("/delete/1")
                .param("currentFolderId", "5")
                .with(user(principal))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?folderId=5"))
                .andExpect(flash().attribute("message", "ゴミ箱に移動しました。"));

        verify(fileService).softDeleteFile(1L);
    }

    @Test
    void showsTheTrash() throws Exception {
        FileEntity deleted = ownedFile(3L, "deleted.txt");
        when(fileService.listDeletedFiles()).thenReturn(List.of(deleted));

        mockMvc.perform(get("/trash").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("trash"))
                .andExpect(model().attribute("files", List.of(deleted)));
    }

    @Test
    void restoresFromTheTrash() throws Exception {
        mockMvc.perform(post("/restore/1").with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/trash"))
                .andExpect(flash().attribute("message", "復元しました。"));

        verify(fileService).restoreFile(1L);
    }

    @Test
    void searchesByName() throws Exception {
        FileEntity hit = ownedFile(4L, "report.pdf");
        when(fileService.searchFiles("report", null)).thenReturn(List.of(hit));

        mockMvc.perform(get("/search").param("query", "report").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(model().attribute("files", List.of(hit)))
                .andExpect(model().attribute("hasCriteria", true));
    }

    @Test
    void searchWithoutCriteriaListsNothing() throws Exception {
        // Returning every readable file for an empty query reads as a result
        // set when it should read as an empty form.
        mockMvc.perform(get("/search").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("files", List.of()))
                .andExpect(model().attribute("hasCriteria", false));

        verify(fileService, never()).searchFiles(any(), any());
    }

    @Test
    void listsFoldersForTheMoveDialogUnderTheSessionAuthenticatedPath() throws Exception {
        // /web/api/** authenticates with the session; /api/** is the stateless
        // Basic-auth surface and is not reachable from the page's fetch calls.
        when(fileService.listFiles(isNull())).thenReturn(List.of());

        mockMvc.perform(get("/web/api/folders").with(user(principal)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/folders").with(user(principal)))
                .andExpect(status().isNotFound());
    }

    @Test
    void changesFileOwnerThroughTheAdminAction() throws Exception {
        mockMvc.perform(post("/chown/7")
                .param("ownerUserId", "2")
                .param("ownerGroupId", "3")
                .param("recursive", "true")
                .with(user(principal))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "所有者・グループを変更しました。"));

        verify(fileService).changeOwner(eq(7L), eq(2L), eq(3L), eq(true));
    }

    @Test
    void togglesFileLock() throws Exception {
        mockMvc.perform(post("/files/9/lock")
                .param("locked", "true")
                .with(user(principal))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("message", "ファイルをロックしました。"));

        verify(fileService).updateLockStatus(eq(9L), eq(true));
    }

    @Test
    void unknownFileIdsSurfaceAsAMessage() throws Exception {
        doThrow(new com.example.filemanager.exception.ResourceNotFoundException("ファイルが見つかりません。"))
                .when(fileService).softDeleteFile(anyLong());

        mockMvc.perform(post("/delete/404").with(user(principal)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "ファイルが見つかりません。"));
    }
}
