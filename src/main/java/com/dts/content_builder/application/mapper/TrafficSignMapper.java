
package com.dts.content_builder.application.mapper;

import com.dts.content_builder.api.form.CreateTrafficSignRequest;
import com.dts.content_builder.api.response.TrafficSignResponse;
import com.dts.content_builder.domain.entity.TrafficSignEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.UUID;

@Component
public class TrafficSignMapper {

    public TrafficSignEntity toEntity(
            CreateTrafficSignRequest request,
            UUID userId,
            TrafficSignEntity.Status status
    ) {
        return TrafficSignEntity.builder()
                .sourceId(request.getSourceId())
                .bankVersion(request.getBankVersion())
                .code(request.getCode())
                .name(request.getName())
                .category(request.getCategory())
                .categoryName(request.getCategoryName())
                .meaning(request.getMeaning())
                .quickTip(request.getQuickTip())
                .sourceImage(request.getSourceImage())
                .mediaFileId(request.getMediaFileId())
                .applicableLicenses(
                        request.getApplicableLicenses() == null
                                ? null
                                : new ArrayList<>(request.getApplicableLicenses())
                )
                .status(status)
                .createdBy(userId)
                .build();
    }

    public TrafficSignResponse toResponse(
            TrafficSignEntity entity
    ) {
        return TrafficSignResponse.builder()
                .id(entity.getId())
                .sourceId(entity.getSourceId())
                .bankVersion(entity.getBankVersion())
                .code(entity.getCode())
                .name(entity.getName())
                .category(entity.getCategory())
                .categoryName(entity.getCategoryName())
                .meaning(entity.getMeaning())
                .quickTip(entity.getQuickTip())
                .sourceImage(entity.getSourceImage())
                .mediaFileId(entity.getMediaFileId())
                .applicableLicenses(
                        entity.getApplicableLicenses() == null
                                ? null
                                : new ArrayList<>(entity.getApplicableLicenses())
                )
                .status(
                        entity.getStatus() == null
                                ? null
                                : entity.getStatus().name()
                )
                .publishedAt(entity.getPublishedAt())
                .build();
    }
}
