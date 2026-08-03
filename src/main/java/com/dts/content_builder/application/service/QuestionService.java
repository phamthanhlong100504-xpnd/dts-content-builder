package com.dts.content_builder.application.service;

import com.dts.content_builder.api.form.CreateQuestionRequest;
import com.dts.content_builder.api.form.QuestionOptionItemRequest;
import com.dts.content_builder.api.form.UpdateQuestionRequest;
import com.dts.content_builder.api.response.PageResponse;
import com.dts.content_builder.api.response.QuestionResponse;
import com.dts.content_builder.application.enums.QuestionStatus;
import com.dts.content_builder.application.enums.QuestionType;
import com.dts.content_builder.application.exception.BusinessValidationException;
import com.dts.content_builder.application.exception.ResourceNotFoundException;
import com.dts.content_builder.application.mapper.QuestionMapper;
import com.dts.content_builder.api.response.InternalQuestionMetadataResponse;
import com.dts.content_builder.domain.entity.ChapterBlockEntity;
import com.dts.content_builder.domain.entity.QuestionBlockEntity;
import com.dts.content_builder.domain.entity.QuestionEntity;
import com.dts.content_builder.domain.entity.QuestionOptionEntity;
import com.dts.content_builder.domain.repository.ChapterBlockRepository;
import com.dts.content_builder.domain.repository.QuestionBlockRepository;
import com.dts.content_builder.domain.repository.QuestionOptionRepository;
import com.dts.content_builder.domain.repository.QuestionBlockRepository;
import com.dts.content_builder.domain.repository.QuestionOptionRepository;
import com.dts.content_builder.domain.repository.QuestionRepository;
import com.dts.content_builder.domain.specification.QuestionSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuestionBlockRepository questionBlockRepository;
    private final ChapterBlockRepository chapterBlockRepository;
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
    public PageResponse<QuestionResponse> listQuestions(String keyword, QuestionType type, QuestionStatus status, UUID createdBy, Pageable pageable) {
        Page<QuestionEntity> page = questionRepository.findAll(
                QuestionSpecification.filterQuestions(keyword, type, status, createdBy),
                pageable
        );

        Page<QuestionResponse> responsePage = page.map(question -> questionMapper.toResponse(question, null));
        return PageResponse.of(responsePage);
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

    @Transactional(readOnly = true)
    public List<InternalQuestionMetadataResponse> getQuestionsMetadataForExam(UUID contentId, String contentType) {
        List<UUID> questionIds = new ArrayList<>();

        if ("CHAPTER".equals(contentType)) {
            List<QuestionBlockEntity> qbList = questionBlockRepository.findByChapterIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(contentId);
            questionIds = qbList.stream().map(QuestionBlockEntity::getQuestionId).filter(java.util.Objects::nonNull).collect(Collectors.toList());
        } else if ("LEARNING_PROGRAM".equals(contentType)) {
            List<ChapterBlockEntity> cbList = chapterBlockRepository.findByLearningProgramIdAndDeletedAtIsNull(contentId);
            List<UUID> chapterIds = cbList.stream().map(ChapterBlockEntity::getChapterId).filter(java.util.Objects::nonNull).collect(Collectors.toList());
            if (!chapterIds.isEmpty()) {
                List<QuestionBlockEntity> qbList = questionBlockRepository.findByChapterIdInAndDeletedAtIsNull(chapterIds);
                questionIds = qbList.stream().map(QuestionBlockEntity::getQuestionId).filter(java.util.Objects::nonNull).collect(Collectors.toList());
            }
        } else {
            throw new BusinessValidationException("Invalid contentType. Must be CHAPTER or LEARNING_PROGRAM");
        }

        if (questionIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<QuestionOptionEntity> allOptions = questionOptionRepository.findByQuestionIdInAndDeletedAtIsNull(questionIds);
        Map<UUID, List<UUID>> optionsMap = allOptions.stream()
                .collect(Collectors.groupingBy(
                        QuestionOptionEntity::getQuestionId,
                        Collectors.mapping(QuestionOptionEntity::getId, Collectors.toList())
                ));

        return questionIds.stream()
                .map(qId -> InternalQuestionMetadataResponse.builder()
                        .id(qId)
                        .optionIds(optionsMap.getOrDefault(qId, Collections.emptyList()))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<com.dts.content_builder.api.response.InternalQuestionDetailResponse> getQuestionsBatch(List<UUID> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<QuestionEntity> questions = questionRepository.findByIdInAndDeletedAtIsNull(questionIds);
        List<QuestionOptionEntity> allOptions = questionOptionRepository.findByQuestionIdInAndDeletedAtIsNull(questionIds);
        
        Map<UUID, List<com.dts.content_builder.api.response.InternalQuestionOptionResponse>> optionsMap = allOptions.stream()
                .collect(Collectors.groupingBy(
                        QuestionOptionEntity::getQuestionId,
                        Collectors.mapping(
                                opt -> com.dts.content_builder.api.response.InternalQuestionOptionResponse.builder()
                                        .id(opt.getId())
                                        .content(opt.getContent())
                                        .sortOrder(opt.getSortOrder())
                                        .isCorrect(opt.getIsCorrect())
                                        .build(),
                                Collectors.toList()
                        )
                ));

        return questions.stream()
                .map(q -> com.dts.content_builder.api.response.InternalQuestionDetailResponse.builder()
                        .id(q.getId())
                        .content(q.getContent())
                        .type(q.getType().name())
                        .options(optionsMap.getOrDefault(q.getId(), Collections.emptyList()))
                        .build())
                .collect(Collectors.toList());
    }
}
