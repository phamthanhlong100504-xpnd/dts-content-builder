package com.dts.content_builder.application.service;

import com.dts.content_builder.api.form.CreateQuestionRequest;
import com.dts.content_builder.api.form.QuestionOptionItemRequest;
import com.dts.content_builder.api.form.UpdateQuestionRequest;
import com.dts.content_builder.api.response.QuestionResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuestionMapper questionMapper;

    public QuestionResponse createQuestion(CreateQuestionRequest request, UUID userId, QuestionStatus status) {
        validateBusinessRules(request, status);

        QuestionEntity question = questionMapper.toEntity(request, userId, status);
        question = questionRepository.save(question);

        List<QuestionOptionEntity> savedOptions = new ArrayList<>();
        if (request.getOptions() != null && !request.getOptions().isEmpty()) {
            int sortIndex = 0;
            for (QuestionOptionItemRequest optionReq : request.getOptions()) {
                if (optionReq.getSortOrder() == null) {
                    optionReq.setSortOrder(sortIndex++);
                }
                QuestionOptionEntity optionEntity = questionMapper.toOptionEntity(optionReq, question.getId(), userId, status);
                savedOptions.add(optionEntity);
            }
            savedOptions = questionOptionRepository.saveAll(savedOptions);
        }

        return questionMapper.toResponse(question, savedOptions);
    }

    @Transactional(readOnly = true)
    public QuestionResponse getQuestionDetail(UUID id, boolean includeOptions) {
        QuestionEntity question = questionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with ID: " + id));

        List<QuestionOptionEntity> options = null;
        if (includeOptions) {
            options = questionOptionRepository.findByQuestionIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(id);
        }

        return questionMapper.toResponse(question, options);
    }

    public QuestionResponse updateQuestion(UUID id, UpdateQuestionRequest request, UUID userId) {
        QuestionEntity question = questionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with ID: " + id));

        if (!question.getCreatedBy().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to update this question.");
        }

        if (request.getStatus() == QuestionStatus.PUBLISHED && question.getStatus() == QuestionStatus.DRAFT) {
            List<QuestionOptionEntity> options = questionOptionRepository.findByQuestionIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(id);
            if (options.size() < 2) {
                throw new BusinessValidationException("Cannot publish question without at least 2 valid options.");
            }
            long correctCount = options.stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).count();
            if (correctCount == 0) {
                throw new BusinessValidationException("Cannot publish question without at least one correct answer.");
            }
            if (request.getType() == QuestionType.SINGLE_CHOICE && correctCount > 1) {
                throw new BusinessValidationException("SINGLE_CHOICE question can only have exactly 1 correct answer.");
            }
            if (request.getType() == QuestionType.TRUE_FALSE) {
                if (options.size() != 2) {
                    throw new BusinessValidationException("TRUE_FALSE question must have exactly 2 options.");
                }
                if (correctCount != 1) {
                    throw new BusinessValidationException("TRUE_FALSE question must have exactly 1 correct answer.");
                }
            }
        }

        questionMapper.updateEntity(question, request, userId);
        question = questionRepository.save(question);

        return questionMapper.toResponse(question, null);
    }

    public void deleteQuestion(UUID id, UUID userId) {
        QuestionEntity question = questionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with ID: " + id));

        if (!question.getCreatedBy().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to delete this question.");
        }

        questionRepository.softDeleteQuestion(id, userId);
        questionOptionRepository.softDeleteOptionsByQuestionId(id, userId);
    }

    private void validateBusinessRules(CreateQuestionRequest request, QuestionStatus status) {
        List<QuestionOptionItemRequest> options = request.getOptions();
        
        if (status == QuestionStatus.PUBLISHED) {
            if (options == null || options.size() < 2) {
                throw new BusinessValidationException("Cannot publish question without at least 2 valid options.");
            }
            
            long correctCount = options.stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).count();
            if (correctCount == 0) {
                throw new BusinessValidationException("Cannot publish question without at least one correct answer.");
            }
            
            if (request.getType() == QuestionType.SINGLE_CHOICE && correctCount > 1) {
                throw new BusinessValidationException("SINGLE_CHOICE question can only have exactly 1 correct answer.");
            }
            
            if (request.getType() == QuestionType.TRUE_FALSE) {
                if (options.size() != 2) {
                    throw new BusinessValidationException("TRUE_FALSE question must have exactly 2 options.");
                }
                if (correctCount != 1) {
                    throw new BusinessValidationException("TRUE_FALSE question must have exactly 1 correct answer.");
                }
            }
        } else if (status == QuestionStatus.DRAFT) {
            if (options != null && !options.isEmpty()) {
                long correctCount = options.stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).count();
                if (request.getType() == QuestionType.SINGLE_CHOICE && correctCount > 1) {
                    throw new BusinessValidationException("SINGLE_CHOICE question can only have at most 1 correct answer in DRAFT.");
                }
                if (request.getType() == QuestionType.TRUE_FALSE && options.size() > 2) {
                    throw new BusinessValidationException("TRUE_FALSE question cannot have more than 2 options.");
                }
            }
        }
    }
}
