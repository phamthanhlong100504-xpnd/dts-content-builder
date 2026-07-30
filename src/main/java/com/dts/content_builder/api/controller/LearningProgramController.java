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
}
