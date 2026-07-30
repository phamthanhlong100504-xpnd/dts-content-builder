package com.dts.content_builder.application.mapper;

import com.dts.content_builder.api.form.CreateDraftChapterRequest;
import com.dts.content_builder.api.form.CreatePublishedChapterRequest;
import com.dts.content_builder.api.form.QuestionBlockItemRequest;
import com.dts.content_builder.api.form.UpdateChapterRequest;
import com.dts.content_builder.api.response.ChapterDetailResponse;
import com.dts.content_builder.api.response.ChapterResponse;
import com.dts.content_builder.api.response.QuestionBlockResponse;
import com.dts.content_builder.domain.entity.ChapterEntity;
import com.dts.content_builder.domain.entity.QuestionBlockEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ChapterMapper {

    public ChapterEntity toEntity(CreateDraftChapterRequest request) {
        return ChapterEntity.builder()
                .title(request.getTitle())
                .metadata(request.getMetadata())
                .build();
    }

    public ChapterEntity toEntity(CreatePublishedChapterRequest request) {
        return ChapterEntity.builder()
                .title(request.getTitle())
                .metadata(request.getMetadata())
                .build();
    }

    public void updateEntity(ChapterEntity entity, UpdateChapterRequest request) {
        entity.setTitle(request.getTitle());
        entity.setStatus(request.getStatus());
        entity.setMetadata(request.getMetadata());
    }

    public ChapterResponse toResponse(ChapterEntity entity) {
        return ChapterResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .status(entity.getStatus())
                .metadata(entity.getMetadata())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedBy(entity.getUpdatedBy())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public ChapterDetailResponse toDetailResponse(ChapterEntity entity) {
        ChapterDetailResponse response = new ChapterDetailResponse();
        response.setId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setStatus(entity.getStatus());
        response.setMetadata(entity.getMetadata());
        response.setCreatedBy(entity.getCreatedBy());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedBy(entity.getUpdatedBy());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    public QuestionBlockEntity toEntity(QuestionBlockItemRequest request) {
        return QuestionBlockEntity.builder()
                .parentId(request.getParentId())
                .questionId(request.getQuestionId())
                .title(request.getTitle())
                .sortOrder(request.getSortOrder())
                .metadata(request.getMetadata())
                .build();
    }

    public QuestionBlockResponse toResponse(QuestionBlockEntity entity) {
        return QuestionBlockResponse.builder()
                .id(entity.getId())
                .chapterId(entity.getChapterId())
                .questionId(entity.getQuestionId())
                .parentId(entity.getParentId())
                .title(entity.getTitle())
                .sortOrder(entity.getSortOrder())
                .status(entity.getStatus())
                .metadata(entity.getMetadata())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedBy(entity.getUpdatedBy())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public List<QuestionBlockResponse> toResponseList(List<QuestionBlockEntity> entities) {
        if (entities == null) {
            return null;
        }
        return entities.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
}
