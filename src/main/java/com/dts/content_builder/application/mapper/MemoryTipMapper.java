
package com.dts.content_builder.application.mapper;

import com.dts.content_builder.api.form.CreateMemoryTipRequest;
import com.dts.content_builder.api.response.MemoryTipResponse;
import com.dts.content_builder.domain.entity.MemoryTipEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MemoryTipMapper {

    // Chuyen request thanh entity de luu vao database
    public MemoryTipEntity toEntity(
            CreateMemoryTipRequest request,
            UUID userId,
            MemoryTipEntity.Status status
    ) {
        return MemoryTipEntity.builder()
                .sourceId(request.getSourceId())
                .bankVersion(request.getBankVersion())
                .sortOrder(request.getSortOrder())
                .title(request.getTitle())
                .quickRule(request.getQuickRule())
                .explanation(request.getExplanation())
                .status(status)
                .createdBy(userId)
                .build();
    }

    // Chuyen entity thanh response tra ve API
    public MemoryTipResponse toResponse(
            MemoryTipEntity entity
    ) {
        return MemoryTipResponse.builder()
                .id(entity.getId())
                .sourceId(entity.getSourceId())
                .bankVersion(entity.getBankVersion())
                .sortOrder(entity.getSortOrder())
                .title(entity.getTitle())
                .quickRule(entity.getQuickRule())
                .explanation(entity.getExplanation())
                .status(
                        entity.getStatus() == null
                                ? null
                                : entity.getStatus().name()
                )
                .publishedAt(entity.getPublishedAt())
                .build();
    }
}
