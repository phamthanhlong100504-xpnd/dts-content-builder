package com.dts.content_builder.api.controller;

import com.dts.content_builder.api.response.InternalQuestionMetadataResponse;
import com.dts.content_builder.application.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/content-builder/internal/questions")
@RequiredArgsConstructor
public class InternalQuestionController {

    private final QuestionService questionService;

    @GetMapping("/metadata")
    public List<InternalQuestionMetadataResponse> getQuestionsMetadata(
            @RequestParam("contentId") UUID contentId,
            @RequestParam("contentType") String contentType) {
        return questionService.getQuestionsMetadataForExam(contentId, contentType);
    }

    @org.springframework.web.bind.annotation.PostMapping("/batch")
    public List<com.dts.content_builder.api.response.InternalQuestionDetailResponse> getQuestionsBatch(
            @org.springframework.web.bind.annotation.RequestBody List<UUID> questionIds) {
        return questionService.getQuestionsBatch(questionIds);
    }
}
