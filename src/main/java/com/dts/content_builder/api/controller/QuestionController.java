package com.dts.content_builder.api.controller;

import com.dts.content_builder.api.form.CreateQuestionRequest;
import com.dts.content_builder.api.response.QuestionResponse;
import com.dts.content_builder.application.enums.QuestionStatus;
import com.dts.content_builder.application.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/content-builder/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;

    @PostMapping("/draft")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionResponse createDraftQuestion(
            @Valid @RequestBody CreateQuestionRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Request-ID", required = false) String traceId) {
        
        // Mock extracting user ID for now since security is not fully implemented
        // Normally this would come from SecurityContextHolder
        UUID mockUserId = UUID.randomUUID();
        
        return questionService.createQuestion(request, mockUserId, QuestionStatus.DRAFT);
    }

    @PostMapping("/published")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionResponse createPublishedQuestion(
            @Valid @RequestBody CreateQuestionRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Request-ID", required = false) String traceId) {
        
        // Mock extracting user ID for now
        UUID mockUserId = UUID.randomUUID();
        
        return questionService.createQuestion(request, mockUserId, QuestionStatus.PUBLISHED);
    }
}
