package com.dts.content_builder.api.controller;

import com.dts.content_builder.api.response.LearningProgramResponse;
import com.dts.content_builder.api.response.PageResponse;
import com.dts.content_builder.application.service.LearningProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/content-builder/learning-programs")
@RequiredArgsConstructor
public class LearningProgramController {

    private final LearningProgramService learningProgramService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_learning-programs:read')")
    public PageResponse<LearningProgramResponse> listLearningPrograms(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID createdBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return learningProgramService.listLearningPrograms(keyword, code, status, createdBy, page, size, sort);
    }

    @org.springframework.web.bind.annotation.PostMapping("/draft")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_learning-programs:create')")
    public LearningProgramResponse createDraftLearningProgram(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody com.dts.content_builder.api.form.CreateLearningProgramRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal UUID userId) {
        return learningProgramService.createDraft(request, userId);
    }

    @org.springframework.web.bind.annotation.PostMapping("/published")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_learning-programs:create')")
    public LearningProgramResponse createPublishedLearningProgram(
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody com.dts.content_builder.api.form.CreatePublishedLearningProgramRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal UUID userId) {
        return learningProgramService.createPublished(request, userId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_learning-programs:read')")
    public LearningProgramResponse getLearningProgramDetail(
            @org.springframework.web.bind.annotation.PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean includeChapterBlocks) {
        return learningProgramService.getDetail(id, includeChapterBlocks);
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_learning-programs:update')")
    public LearningProgramResponse updateLearningProgram(
            @org.springframework.web.bind.annotation.PathVariable UUID id,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody com.dts.content_builder.api.form.UpdateLearningProgramRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal UUID userId) {
        return learningProgramService.update(id, request, userId);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_learning-programs:delete')")
    public void deleteLearningProgram(
            @org.springframework.web.bind.annotation.PathVariable UUID id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal UUID userId) {
        learningProgramService.delete(id, userId);
    }
}
