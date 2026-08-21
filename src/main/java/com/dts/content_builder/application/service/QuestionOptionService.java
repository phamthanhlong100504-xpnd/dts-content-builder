package com.dts.content_builder.application.service;

import com.dts.content_builder.api.form.AddQuestionOptionRequest;
import com.dts.content_builder.api.form.ReorderItemRequest;
import com.dts.content_builder.api.form.UpdateQuestionOptionRequest;
import com.dts.content_builder.api.response.QuestionOptionResponse;
import com.dts.content_builder.application.enums.QuestionStatus;
import com.dts.content_builder.application.enums.QuestionType;
import com.dts.content_builder.application.exception.BusinessValidationException;
import com.dts.content_builder.application.exception.ResourceNotFoundException;
import com.dts.content_builder.application.mapper.QuestionMapper;
import com.dts.content_builder.domain.entity.QuestionEntity;
import com.dts.content_builder.domain.entity.QuestionOptionEntity;
import com.dts.content_builder.domain.repository.QuestionOptionRepository;
import com.dts.content_builder.domain.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class QuestionOptionService {

    private final QuestionOptionRepository optionRepository;
    private final QuestionRepository questionRepository;
    private final QuestionMapper questionMapper;

    public QuestionOptionResponse addOption(UUID questionId, AddQuestionOptionRequest request, UUID userId) {
        QuestionEntity question = questionRepository.findByIdAndDeletedAtIsNull(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

        if (!question.getCreatedBy().equals(userId)) {
            throw new AccessDeniedException("You do not have permission to modify this question.");
        }

        if (Boolean.TRUE.equals(request.getIsCorrect()) && question.getType() == QuestionType.SINGLE_CHOICE) {
            List<QuestionOptionEntity> existingOptions = optionRepository.findByQuestionIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(questionId);
            boolean hasCorrect = existingOptions.stream().anyMatch(QuestionOptionEntity::getIsCorrect);
            if (hasCorrect) {
                throw new BusinessValidationException("Cannot add correct option: Single choice question already has a correct answer.");
            }
        }

        int sortOrder = request.getSortOrder() != null ? request.getSortOrder() : optionRepository.findMaxSortOrderByQuestionId(questionId) + 1;

        QuestionOptionEntity option = QuestionOptionEntity.builder()
                .id(UUID.randomUUID())
                .questionId(questionId)
                .content(request.getContent())
                .sortOrder(sortOrder)
                .isCorrect(request.getIsCorrect())
                .status(request.getStatus() != null ? request.getStatus() : QuestionStatus.DRAFT)
                .metadata(request.getMetadata())
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .build();

        option = optionRepository.save(option);
        return questionMapper.toOptionResponse(option);
    }

    public QuestionOptionResponse updateOption(UUID questionId, UUID optionId, UpdateQuestionOptionRequest request, UUID userId) {
        QuestionEntity question = questionRepository.findByIdAndDeletedAtIsNull(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

        if (!question.getCreatedBy().equals(userId)) {
            throw new AccessDeniedException("You do not have permission to modify this question.");
        }

        QuestionOptionEntity option = optionRepository.findByIdAndQuestionIdAndDeletedAtIsNull(optionId, questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Option not found in the specified question."));

        if (question.getStatus() == QuestionStatus.PUBLISHED) {
            throw new BusinessValidationException("Cannot update an option of a PUBLISHED question. Please change the question status to ARCHIVED/HIDDEN and create a new version.");
        }

        if (Boolean.TRUE.equals(request.getIsCorrect()) && !option.getIsCorrect() && question.getType() == QuestionType.SINGLE_CHOICE) {
            List<QuestionOptionEntity> existingOptions = optionRepository.findByQuestionIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(questionId);
            boolean hasOtherCorrect = existingOptions.stream().anyMatch(o -> o.getIsCorrect() && !o.getId().equals(optionId));
            if (hasOtherCorrect) {
                throw new BusinessValidationException("Cannot change option to correct: Single choice question already has another correct answer.");
            }
        }

        option.setContent(request.getContent());
        option.setSortOrder(request.getSortOrder());
        option.setIsCorrect(request.getIsCorrect());
        option.setStatus(request.getStatus());
        option.setMetadata(request.getMetadata());
        option.setUpdatedBy(userId);
        option.setUpdatedAt(LocalDateTime.now());

        option = optionRepository.save(option);
        return questionMapper.toOptionResponse(option);
    }

    public void deleteOption(UUID questionId, UUID optionId, UUID userId) {
        QuestionEntity question = questionRepository.findByIdAndDeletedAtIsNull(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

        if (!question.getCreatedBy().equals(userId)) {
            throw new AccessDeniedException("You do not have permission to delete this option.");
        }

        QuestionOptionEntity option = optionRepository.findByIdAndQuestionIdAndDeletedAtIsNull(optionId, questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Option not found."));

        if (question.getStatus() == QuestionStatus.PUBLISHED) {
            throw new BusinessValidationException("Cannot delete an option from a PUBLISHED question. Please create a new version of the question instead.");
        }

        option.setDeletedAt(LocalDateTime.now());
        option.setUpdatedBy(userId);
        optionRepository.save(option);
    }

    public List<QuestionOptionResponse> reorderOptions(UUID questionId, List<ReorderItemRequest> items, UUID userId) {
        if (items == null || items.isEmpty()) {
            throw new BusinessValidationException("Invalid reorder request payload.");
        }

        QuestionEntity question = questionRepository.findByIdAndDeletedAtIsNull(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));

        if (!question.getCreatedBy().equals(userId)) {
            throw new AccessDeniedException("You do not have permission to modify this question.");
        }

        Set<UUID> uniqueIds = new HashSet<>();
        Set<Integer> uniqueSortOrders = new HashSet<>();
        for (ReorderItemRequest item : items) {
            if (!uniqueIds.add(item.getId()) || !uniqueSortOrders.add(item.getSortOrder())) {
                throw new BusinessValidationException("Duplicate IDs or sortOrder indices in request payload.");
            }
        }

        List<UUID> payloadIds = items.stream().map(ReorderItemRequest::getId).collect(Collectors.toList());
        List<QuestionOptionEntity> optionsToUpdate = optionRepository.findByIdInAndQuestionIdAndDeletedAtIsNull(payloadIds, questionId);

        if (optionsToUpdate.size() != payloadIds.size()) {
            throw new ResourceNotFoundException("One or more options not found in this question.");
        }

        for (QuestionOptionEntity option : optionsToUpdate) {
            for (ReorderItemRequest item : items) {
                if (option.getId().equals(item.getId())) {
                    option.setSortOrder(item.getSortOrder());
                    option.setUpdatedBy(userId);
                    option.setUpdatedAt(LocalDateTime.now());
                    break;
                }
            }
        }

        optionRepository.saveAll(optionsToUpdate);

        List<QuestionOptionEntity> allOptions = optionRepository.findByQuestionIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(questionId);
        return allOptions.stream().map(questionMapper::toOptionResponse).collect(Collectors.toList());
    }
}
