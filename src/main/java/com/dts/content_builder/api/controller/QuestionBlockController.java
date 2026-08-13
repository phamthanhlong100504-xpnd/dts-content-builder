package com.dts.content_builder.api.controller;

import com.dts.content_builder.api.form.CreateQuestionBlockRequest;
import com.dts.content_builder.api.form.ReorderItem;
import com.dts.content_builder.api.form.UpdateQuestionBlockRequest;
import com.dts.content_builder.api.response.QuestionBlockResponse;
import com.dts.content_builder.application.service.ChapterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/content-builder/chapters/{chapterId}/question-blocks")
@RequiredArgsConstructor
public class QuestionBlockController {

    private final ChapterService chapterService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_chapters:update')")
    public QuestionBlockResponse addQuestionBlock(
            @PathVariable UUID chapterId,
            @Valid @RequestBody CreateQuestionBlockRequest request,
            @AuthenticationPrincipal UUID userId) {
        return chapterService.addQuestionBlock(chapterId, request, userId);
    }

    @PutMapping("/{blockId}")
    @PreAuthorize("hasAuthority('PERM_chapters:update')")
    public QuestionBlockResponse updateQuestionBlock(
            @PathVariable UUID chapterId,
            @PathVariable UUID blockId,
            @Valid @RequestBody UpdateQuestionBlockRequest request,
            @AuthenticationPrincipal UUID userId) {
        return chapterService.updateQuestionBlock(chapterId, blockId, request, userId);
    }

    @DeleteMapping("/{blockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_chapters:update')")
    public void deleteQuestionBlock(
            @PathVariable UUID chapterId,
            @PathVariable UUID blockId,
            @AuthenticationPrincipal UUID userId) {
        chapterService.deleteQuestionBlock(chapterId, blockId, userId);
    }

    @PutMapping("/reorder")
    @PreAuthorize("hasAuthority('PERM_chapters:update')")
    public List<QuestionBlockResponse> reorderQuestionBlocks(
            @PathVariable UUID chapterId,
            @RequestBody List<@Valid ReorderItem> request,
            @AuthenticationPrincipal UUID userId) {
        return chapterService.reorderQuestionBlocks(chapterId, request, userId);
    }
}
