package com.dts.content_builder.application.mapper;

import com.dts.content_builder.api.form.CreateQuestionRequest;
import com.dts.content_builder.api.form.QuestionOptionItemRequest;
import com.dts.content_builder.api.form.UpdateQuestionRequest;
import com.dts.content_builder.api.response.QuestionOptionResponse;
import com.dts.content_builder.api.response.QuestionResponse;
import com.dts.content_builder.application.enums.QuestionStatus;
import com.dts.content_builder.domain.entity.QuestionEntity;
import com.dts.content_builder.domain.entity.QuestionOptionEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class QuestionMapper {

    public QuestionEntity toEntity(CreateQuestionRequest request, UUID userId, QuestionStatus status) {
        return QuestionEntity.builder()
                .id(UUID.randomUUID()) // Or UUIDv7
                .type(request.getType())
                .content(request.getContent())
                .explanations(request.getExplanations())
                .mediaFileIds(request.getMediaFileIds())
                .attachments(request.getAttachments())
                .references(request.getReferences())
                .status(status)
                .metadata(request.getMetadata())
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public void updateEntity(QuestionEntity entity, UpdateQuestionRequest request, UUID userId) {
        entity.setType(request.getType());
        entity.setContent(request.getContent());
        entity.setExplanations(request.getExplanations());
        entity.setMediaFileIds(request.getMediaFileIds());
        entity.setAttachments(request.getAttachments());
        entity.setReferences(request.getReferences());
        entity.setStatus(request.getStatus());
        entity.setMetadata(request.getMetadata());
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());
    }

    public QuestionOptionEntity toOptionEntity(QuestionOptionItemRequest request, UUID questionId, UUID userId, QuestionStatus status) {
        return QuestionOptionEntity.builder()
                .id(UUID.randomUUID()) // Or UUIDv7
                .questionId(questionId)
                .content(request.getContent())
                .sortOrder(request.getSortOrder())
                .isCorrect(request.getIsCorrect())
                .status(status)
                .metadata(request.getMetadata())
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public QuestionResponse toResponse(QuestionEntity entity, List<QuestionOptionEntity> options) {
        List<QuestionOptionResponse> optionResponses = options != null ? options.stream()
                .map(this::toOptionResponse)
                .collect(Collectors.toList()) : null;

        return QuestionResponse.builder()
                .id(entity.getId())
                .type(entity.getType())
                .content(entity.getContent())
                .explanations(entity.getExplanations())
                .mediaFileIds(entity.getMediaFileIds())
                .attachments(entity.getAttachments())
                .references(entity.getReferences())
                .status(entity.getStatus())
                .metadata(entity.getMetadata())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedBy(entity.getUpdatedBy())
                .updatedAt(entity.getUpdatedAt())
                .options(optionResponses)
                .build();
    }

    public QuestionOptionResponse toOptionResponse(QuestionOptionEntity entity) {
        return QuestionOptionResponse.builder()
                .id(entity.getId())
                .content(entity.getContent())
                .sortOrder(entity.getSortOrder())
                .isCorrect(entity.getIsCorrect())
                .status(entity.getStatus())
                .metadata(entity.getMetadata())
                .build();
    }
}
