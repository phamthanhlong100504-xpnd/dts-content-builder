
package com.dts.content_builder.api.controller;

import com.dts.content_builder.api.form.CreateTrafficSignRequest;
import com.dts.content_builder.api.response.PageResponse;
import com.dts.content_builder.api.response.TrafficSignResponse;
import com.dts.content_builder.application.service.TrafficSignService;

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
@RequestMapping("/api/v1/content-builder/traffic-signs")
@RequiredArgsConstructor
public class TrafficSignController {

    private final TrafficSignService trafficSignService;

    // Tao ban nhap
    @PostMapping("/draft")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TrafficSignResponse createDraft(
            @Valid @RequestBody CreateTrafficSignRequest request,
            @AuthenticationPrincipal UUID userId
    ) {
        return trafficSignService.createDraft(request, userId);
    }

    // Tao bien bao da xuat ban
    @PostMapping("/published")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TrafficSignResponse createPublished(
            @Valid @RequestBody CreateTrafficSignRequest request,
            @AuthenticationPrincipal UUID userId
    ) {
        return trafficSignService.createPublished(request, userId);
    }

    // Lay danh sach bien bao da PUBLISHED
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageResponse<TrafficSignResponse> listPublished(
            @RequestParam String bankVersion,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @PageableDefault(
                    size = 24,
                    sort = "sourceId",
                    direction = Sort.Direction.ASC
            ) Pageable pageable
    ) {
        return trafficSignService.listPublished(
                bankVersion,
                category,
                keyword,
                pageable
        );
    }

    // Lay chi tiet bien bao da PUBLISHED
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public TrafficSignResponse getPublishedById(
            @PathVariable UUID id
    ) {
        return trafficSignService.getPublishedById(id);
    }
}
