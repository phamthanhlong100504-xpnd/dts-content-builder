package com.dts.content_builder.api.controller;

import com.dts.content_builder.api.response.InternalQuestionDetailResponse;
import com.dts.content_builder.api.response.InternalQuestionMetadataResponse;
import com.dts.content_builder.application.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
            @RequestParam("contentType") String contentType,
            @RequestParam(value = "licenseClass", required = false) String licenseClass) {
        return questionService.getQuestionsMetadataForExam(
                contentId, contentType, licenseClass);
    }

    @PostMapping("/batch")
    public List<InternalQuestionDetailResponse> getQuestionsBatch(
            @RequestBody List<UUID> questionIds,
            @RequestParam(value = "licenseClass", required = false)
            String licenseClass) {
        return questionService.getQuestionsBatchForLicense(
                questionIds,
                licenseClass
        );
    }
}