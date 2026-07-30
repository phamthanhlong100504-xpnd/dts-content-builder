package com.dts.content_builder.api.controller;

import com.dts.content_builder.api.form.CreateChapterBlockRequest;
import com.dts.content_builder.api.form.ReorderItem;
import com.dts.content_builder.api.form.UpdateChapterBlockRequest;
import com.dts.content_builder.api.response.ChapterBlockDetailResponse;
import com.dts.content_builder.api.response.ChapterBlockTreeResponse;
import com.dts.content_builder.application.service.ChapterBlockService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/content-builder/learning-programs/{learningProgramId}/chapter-blocks")
@RequiredArgsConstructor
public class ChapterBlockController {

    private final ChapterBlockService chapterBlockService;

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority('PERM_learning-programs:read')")
    public List<ChapterBlockTreeResponse> getChapterBlockTree(@PathVariable UUID learningProgramId) {
        return chapterBlockService.getChapterBlockTree(learningProgramId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_learning-programs:update')")
    public ChapterBlockDetailResponse addChapterBlock(
            @PathVariable UUID learningProgramId,
            @Valid @RequestBody CreateChapterBlockRequest request,
            @AuthenticationPrincipal UUID userId) {
        return chapterBlockService.addChapterBlock(learningProgramId, request, userId);
    }

    @PutMapping("/{blockId}")
    @PreAuthorize("hasAuthority('PERM_learning-programs:update')")
    public ChapterBlockDetailResponse updateChapterBlock(
            @PathVariable UUID learningProgramId,
            @PathVariable UUID blockId,
            @Valid @RequestBody UpdateChapterBlockRequest request,
            @AuthenticationPrincipal UUID userId) {
        return chapterBlockService.updateChapterBlock(learningProgramId, blockId, request, userId);
    }

    @DeleteMapping("/{blockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_learning-programs:update')")
    public void deleteChapterBlock(
            @PathVariable UUID learningProgramId,
            @PathVariable UUID blockId,
            @AuthenticationPrincipal UUID userId) {
        chapterBlockService.deleteChapterBlock(learningProgramId, blockId, userId);
    }

    @PutMapping("/reorder")
    @PreAuthorize("hasAuthority('PERM_learning-programs:update')")
    public List<ChapterBlockDetailResponse> reorderChapterBlocks(
            @PathVariable UUID learningProgramId,
            @RequestBody List<@Valid ReorderItem> request,
            @AuthenticationPrincipal UUID userId) {
        return chapterBlockService.reorderChapterBlocks(learningProgramId, request, userId);
    }
}
