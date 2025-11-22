package com.example.filemanager.controller;

import com.example.filemanager.controller.dto.FolderRequest;
import com.example.filemanager.domain.FileEntity;
import com.example.filemanager.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WebController.class)
class WebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileService fileService;

    @Test
    @WithMockUser
    void shouldReturnHomeViewWithFiles() throws Exception {
        FileEntity file1 = new FileEntity();
        file1.setId(1L);
        file1.setName("file1.txt");

        when(fileService.listFiles(null)).thenReturn(Arrays.asList(file1));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(model().attributeExists("files"))
                .andExpect(model().attribute("files", Arrays.asList(file1)));
    }

    @Test
    @WithMockUser
    void shouldReturnHomeViewWithFolderContent() throws Exception {
        Long folderId = 10L;
        FileEntity folder = new FileEntity();
        folder.setId(folderId);
        folder.setName("Docs");

        FileEntity fileInFolder = new FileEntity();
        fileInFolder.setId(2L);
        fileInFolder.setName("doc.txt");

        when(fileService.listFiles(folderId)).thenReturn(Arrays.asList(fileInFolder));
        when(fileService.findFileById(folderId)).thenReturn(folder);

        mockMvc.perform(get("/").param("folderId", folderId.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(model().attribute("files", Arrays.asList(fileInFolder)))
                .andExpect(model().attribute("currentFolderId", folderId))
                .andExpect(model().attribute("currentFolder", folder));
    }

    @Test
    @WithMockUser
    void shouldUploadFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "content".getBytes());

        mockMvc.perform(multipart("/upload")
                .file(file)
                .param("permissions", "644")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(fileService).uploadFile(any(), eq(null), eq("644"));
    }

    @Test
    @WithMockUser
    void shouldCreateFolder() throws Exception {
        mockMvc.perform(post("/folders")
                .param("name", "New Folder")
                .param("permissions", "755")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(fileService).createDirectory(any(FolderRequest.class));
    }

    @Test
    @WithMockUser
    void shouldDeleteFile() throws Exception {
        Long fileId = 1L;

        mockMvc.perform(post("/delete/{id}", fileId)
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(fileService).softDeleteFile(fileId);
    }

    @Test
    @WithMockUser
    void shouldReturnTrashView() throws Exception {
        FileEntity deletedFile = new FileEntity();
        deletedFile.setId(1L);
        deletedFile.setName("deleted.txt");

        when(fileService.listDeletedFiles()).thenReturn(Arrays.asList(deletedFile));

        mockMvc.perform(get("/trash"))
                .andExpect(status().isOk())
                .andExpect(view().name("trash"))
                .andExpect(model().attribute("files", Arrays.asList(deletedFile)));
    }

    @Test
    @WithMockUser
    void shouldRestoreFile() throws Exception {
        Long fileId = 1L;

        mockMvc.perform(post("/restore/{id}", fileId)
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/trash"));

        verify(fileService).restoreFile(fileId);
    }

    @Test
    @WithMockUser
    void shouldSearchFiles() throws Exception {
        String query = "test";
        String tags = "work";
        FileEntity result = new FileEntity();
        result.setName("test-work.txt");

        when(fileService.searchFiles(query, tags)).thenReturn(Arrays.asList(result));

        mockMvc.perform(get("/search")
                .param("query", query)
                .param("tags", tags))
                .andExpect(status().isOk())
                .andExpect(view().name("search"))
                .andExpect(model().attribute("files", Arrays.asList(result)))
                .andExpect(model().attribute("query", query))
                .andExpect(model().attribute("tags", tags));
    }
}
