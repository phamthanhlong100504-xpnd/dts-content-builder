package com.dts.content_builder.api.controller;

import com.dts.content_builder.api.response.InternalQuestionDetailResponse;
import com.dts.content_builder.api.response.InternalQuestionMetadataResponse;
import com.dts.content_builder.api.response.InternalQuestionBankResponse;
import com.dts.content_builder.application.service.ExamQuestionReadService;
import com.dts.content_builder.config.ExamSourceAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/content-builder/internal/questions")
@RequiredArgsConstructor
public class InternalQuestionController {

    private final ExamQuestionReadService examQuestionReadService;
    private final ExamSourceAccess examSourceAccess;

    @GetMapping("/bank")
    public ResponseEntity<InternalQuestionBankResponse> getQuestionBank(
            @RequestParam("bankVersion") String bankVersion,
            @RequestParam("licenseClass") String licenseClass,
            @RequestHeader(value = ExamSourceAccess.HEADER_NAME, required = false) String serviceToken) {
        examSourceAccess.requireAccess(serviceToken);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(examQuestionReadService.getBank(bankVersion, licenseClass));
    }

    @GetMapping("/metadata")
    public ResponseEntity<List<InternalQuestionMetadataResponse>> getQuestionsMetadata(
            @RequestParam("contentId") UUID contentId,
            @RequestParam("contentType") String contentType,
            @RequestParam(value = "licenseClass", required = false) String licenseClass,
            @RequestHeader(value = ExamSourceAccess.HEADER_NAME, required = false) String serviceToken) {
        examSourceAccess.requireAccess(serviceToken);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(examQuestionReadService.getMetadata(contentId, contentType, licenseClass));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<InternalQuestionDetailResponse>> getQuestionsBatch(
            @RequestBody List<UUID> questionIds,
            @RequestParam(value = "licenseClass", required = false) String licenseClass,
            @RequestHeader(value = ExamSourceAccess.HEADER_NAME, required = false) String serviceToken) {
        examSourceAccess.requireAccess(serviceToken);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(examQuestionReadService.getBatch(questionIds, licenseClass));
    }
}
