
package com.dts.content_builder.api.controller;

import com.dts.content_builder.api.form.CreateMemoryTipRequest;
import com.dts.content_builder.api.response.MemoryTipResponse;
import com.dts.content_builder.api.response.PageResponse;
import com.dts.content_builder.application.service.MemoryTipService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/content-builder/memory-tips")
@RequiredArgsConstructor
public class MemoryTipController {

    private final MemoryTipService memoryTipService;

    // Tao meo ghi nho o trang thai DRAFT
    @PostMapping("/draft")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public MemoryTipResponse createDraft(
            @Valid @RequestBody CreateMemoryTipRequest request,
            @AuthenticationPrincipal UUID userId
    ) {
        return memoryTipService.createDraft(request, userId);
    }

    // Tao va xuat ban meo ghi nho
    @PostMapping("/published")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public MemoryTipResponse createPublished(
            @Valid @RequestBody CreateMemoryTipRequest request,
            @AuthenticationPrincipal UUID userId
    ) {
        return memoryTipService.createPublished(request, userId);
    }

    // Lay danh sach meo da PUBLISHED
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<MemoryTipResponse> listPublished(
            @RequestParam String bankVersion,
            @RequestParam(required = false) String keyword,
            @PageableDefault(
                    size = 20,
                    sort = "sortOrder",
                    direction = Sort.Direction.ASC
            ) Pageable pageable
    ) {
        return memoryTipService.listPublished(
                bankVersion,
                keyword,
                pageable
        );
    }

    // Lay chi tiet mot meo da PUBLISHED
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public MemoryTipResponse getPublishedById(
            @PathVariable UUID id
    ) {
        return memoryTipService.getPublishedById(id);
    }
}
