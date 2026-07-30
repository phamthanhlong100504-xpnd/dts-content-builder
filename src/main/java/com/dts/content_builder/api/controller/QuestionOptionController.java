package com.dts.content_builder.api.controller;

import com.dts.content_builder.api.form.AddQuestionOptionRequest;
import com.dts.content_builder.api.form.ReorderItemRequest;
import com.dts.content_builder.api.form.UpdateQuestionOptionRequest;
import com.dts.content_builder.api.response.QuestionOptionResponse;
import com.dts.content_builder.application.service.QuestionOptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/content-builder/questions/{questionId}/options")
@RequiredArgsConstructor
public class QuestionOptionController {

    private final QuestionOptionService questionOptionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_questions:update')")
    public QuestionOptionResponse addOption(
            @PathVariable UUID questionId,
            @Valid @RequestBody AddQuestionOptionRequest request,
            @AuthenticationPrincipal UUID userId) {
        return questionOptionService.addOption(questionId, request, userId);
    }

    @PutMapping("/{optionId}")
    @PreAuthorize("hasAuthority('PERM_questions:update')")
    public QuestionOptionResponse updateOption(
            @PathVariable UUID questionId,
            @PathVariable UUID optionId,
            @Valid @RequestBody UpdateQuestionOptionRequest request,
            @AuthenticationPrincipal UUID userId) {
        return questionOptionService.updateOption(questionId, optionId, request, userId);
    }

    @DeleteMapping("/{optionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_questions:update')")
    public void deleteOption(
            @PathVariable UUID questionId,
            @PathVariable UUID optionId,
            @AuthenticationPrincipal UUID userId) {
        questionOptionService.deleteOption(questionId, optionId, userId);
    }

    @PutMapping("/reorder")
    @PreAuthorize("hasAuthority('PERM_questions:update')")
    public List<QuestionOptionResponse> reorderOptions(
            @PathVariable UUID questionId,
            @Valid @RequestBody List<ReorderItemRequest> request,
            @AuthenticationPrincipal UUID userId) {
        return questionOptionService.reorderOptions(questionId, request, userId);
    }
}
