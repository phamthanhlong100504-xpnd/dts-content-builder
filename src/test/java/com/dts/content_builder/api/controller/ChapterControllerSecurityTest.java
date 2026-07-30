package com.dts.content_builder.api.controller;

import com.dts.content_builder.api.form.CreateDraftChapterRequest;
import com.dts.content_builder.api.form.CreatePublishedChapterRequest;
import com.dts.content_builder.api.form.QuestionBlockItemRequest;
import com.dts.content_builder.api.form.UpdateChapterRequest;
import com.dts.content_builder.application.enums.ChapterStatus;
import com.dts.content_builder.application.service.ChapterService;
import com.dts.content_builder.config.JwtAuthenticationFilter;
import com.dts.content_builder.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ChapterController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@Import(SecurityConfig.class) // Ensures MethodSecurity (PreAuthorize) is active
public class ChapterControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChapterService chapterService;

    // ==========================================
    // TEST POST /draft
    // ==========================================

    @Test
    public void testCreateDraft_NoAuth_Returns401() throws Exception {
        CreateDraftChapterRequest req = new CreateDraftChapterRequest();
        req.setTitle("Test Title");

        mockMvc.perform(post("/api/v1/content-builder/chapters/draft")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"PERM_chapters:read"})
    public void testCreateDraft_WrongPermission_Returns403() throws Exception {
        CreateDraftChapterRequest req = new CreateDraftChapterRequest();
        req.setTitle("Test Title");

        mockMvc.perform(post("/api/v1/content-builder/chapters/draft")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"PERM_chapters:create"})
    public void testCreateDraft_CorrectPermission_Returns201() throws Exception {
        CreateDraftChapterRequest req = new CreateDraftChapterRequest();
        req.setTitle("Test Title");

        mockMvc.perform(post("/api/v1/content-builder/chapters/draft")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    // ==========================================
    // TEST POST /published
    // ==========================================

    @Test
    @WithMockUser(authorities = {"PERM_chapters:create"})
    public void testCreatePublished_MissingUpdatePermission_Returns403() throws Exception {
        CreatePublishedChapterRequest req = new CreatePublishedChapterRequest();
        req.setTitle("Test Title");
        req.setQuestionBlocks(List.of(new QuestionBlockItemRequest(null, null, "Block 1", 0, null)));

        mockMvc.perform(post("/api/v1/content-builder/chapters/published")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"PERM_chapters:create", "PERM_chapters:update"})
    public void testCreatePublished_CorrectPermissions_Returns201() throws Exception {
        CreatePublishedChapterRequest req = new CreatePublishedChapterRequest();
        req.setTitle("Test Title");
        req.setQuestionBlocks(List.of(new QuestionBlockItemRequest(null, null, "Block 1", 0, null)));

        mockMvc.perform(post("/api/v1/content-builder/chapters/published")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    // ==========================================
    // TEST GET /
    // ==========================================

    @Test
    @WithMockUser(authorities = {"PERM_chapters:write"}) // arbitrary wrong perm
    public void testListChapters_WrongPermission_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/content-builder/chapters"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"PERM_chapters:read"})
    public void testListChapters_CorrectPermission_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/content-builder/chapters"))
                .andExpect(status().isOk());
    }

    // ==========================================
    // TEST PUT /{id}
    // ==========================================

    @Test
    @WithMockUser(authorities = {"PERM_chapters:read"})
    public void testUpdateChapter_WrongPermission_Returns403() throws Exception {
        UpdateChapterRequest req = new UpdateChapterRequest();
        req.setTitle("Updated Title");
        req.setStatus(ChapterStatus.PUBLISHED);

        mockMvc.perform(put("/api/v1/content-builder/chapters/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"PERM_chapters:update"})
    public void testUpdateChapter_CorrectPermission_Returns200() throws Exception {
        UpdateChapterRequest req = new UpdateChapterRequest();
        req.setTitle("Updated Title");
        req.setStatus(ChapterStatus.PUBLISHED);

        mockMvc.perform(put("/api/v1/content-builder/chapters/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ==========================================
    // TEST DELETE /{id}
    // ==========================================

    @Test
    @WithMockUser(authorities = {"PERM_chapters:read", "PERM_chapters:update"})
    public void testDeleteChapter_WrongPermission_Returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/content-builder/chapters/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"PERM_chapters:delete"})
    public void testDeleteChapter_CorrectPermission_Returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/content-builder/chapters/" + UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }
}
