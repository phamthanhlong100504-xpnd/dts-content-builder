package com.dts.content_builder.api.controller;

import com.dts.content_builder.api.form.CreateDraftChapterRequest;
import com.dts.content_builder.api.form.CreatePublishedChapterRequest;
import com.dts.content_builder.api.form.UpdateChapterRequest;
import com.dts.content_builder.api.response.ChapterDetailResponse;
import com.dts.content_builder.api.response.ChapterResponse;
import com.dts.content_builder.api.response.PageResponse;
import com.dts.content_builder.application.service.ChapterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/content-builder/chapters")
@RequiredArgsConstructor
public class ChapterController {

    private final ChapterService chapterService;

    @PostMapping("/draft")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_chapters:create')")
    public ChapterResponse createDraftChapter(
            @Valid @RequestBody CreateDraftChapterRequest request,
            @AuthenticationPrincipal UUID userId) {
        
        return chapterService.createDraftChapter(request, userId);
    }

    @PostMapping("/published")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_chapters:create') and hasAuthority('PERM_chapters:update')")
    public ChapterDetailResponse createPublishedChapter(
            @Valid @RequestBody CreatePublishedChapterRequest request,
            @AuthenticationPrincipal UUID userId) {
        
        return chapterService.createPublishedChapter(request, userId);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_chapters:read')")
    public PageResponse<ChapterResponse> listChapters(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID createdBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return chapterService.listChapters(keyword, status, createdBy, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_chapters:read')")
    public ChapterDetailResponse getChapterDetail(@PathVariable UUID id) {
        return chapterService.getChapterDetail(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_chapters:update')")
    public ChapterResponse updateChapter(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateChapterRequest request,
            @AuthenticationPrincipal UUID userId) {
        return chapterService.updateChapter(id, request, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_chapters:delete')")
    public void deleteChapter(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        chapterService.deleteChapter(id, userId);
    }
}
