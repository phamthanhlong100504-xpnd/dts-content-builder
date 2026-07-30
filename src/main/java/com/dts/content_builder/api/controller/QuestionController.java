package com.dts.content_builder.api.controller;

import com.dts.content_builder.api.form.CreateQuestionRequest;
import com.dts.content_builder.api.response.QuestionResponse;
import com.dts.content_builder.application.enums.QuestionStatus;
import com.dts.content_builder.application.service.QuestionService;
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
import com.dts.content_builder.api.form.UpdateQuestionRequest;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/content-builder/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping("/draft")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_questions:create')")
    public QuestionResponse createDraftQuestion(
            @Valid @RequestBody CreateQuestionRequest request,
            @AuthenticationPrincipal UUID userId) {
        
        return questionService.createQuestion(request, userId, QuestionStatus.DRAFT);
    }

    @PostMapping("/published")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_questions:create')")
    public QuestionResponse createPublishedQuestion(
            @Valid @RequestBody CreateQuestionRequest request,
            @AuthenticationPrincipal UUID userId) {
        
        return questionService.createQuestion(request, userId, QuestionStatus.PUBLISHED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_questions:read')")
    public QuestionResponse getQuestionDetail(
            @PathVariable UUID id,
            @RequestParam(required = false, defaultValue = "false") boolean includeOptions) {
        return questionService.getQuestionDetail(id, includeOptions);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_questions:update')")
    public QuestionResponse updateQuestion(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateQuestionRequest request,
            @AuthenticationPrincipal UUID userId) {
        return questionService.updateQuestion(id, request, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_questions:delete')")
    public void deleteQuestion(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        questionService.deleteQuestion(id, userId);
    }
}
