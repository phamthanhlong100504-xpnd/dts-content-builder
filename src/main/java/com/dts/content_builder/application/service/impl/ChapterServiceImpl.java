package com.dts.content_builder.application.service.impl;

import com.dts.content_builder.api.form.CreateDraftChapterRequest;
import com.dts.content_builder.api.form.CreatePublishedChapterRequest;
import com.dts.content_builder.api.form.QuestionBlockItemRequest;
import com.dts.content_builder.api.form.UpdateChapterRequest;
import com.dts.content_builder.api.response.ChapterDetailResponse;
import com.dts.content_builder.api.response.ChapterResponse;
import com.dts.content_builder.api.response.PageResponse;
import com.dts.content_builder.api.response.QuestionBlockResponse;
import com.dts.content_builder.application.enums.ChapterStatus;
import com.dts.content_builder.application.exception.BusinessValidationException;
import com.dts.content_builder.application.exception.ResourceNotFoundException;
import com.dts.content_builder.application.mapper.ChapterMapper;
import com.dts.content_builder.application.service.ChapterService;
import com.dts.content_builder.domain.entity.ChapterEntity;
import com.dts.content_builder.domain.entity.QuestionBlockEntity;
import com.dts.content_builder.domain.repository.ChapterRepository;
import com.dts.content_builder.domain.repository.QuestionBlockRepository;
import com.dts.content_builder.domain.repository.QuestionRepository;
import com.dts.content_builder.domain.specification.ChapterSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ChapterServiceImpl implements ChapterService {

    private final ChapterRepository chapterRepository;
    private final QuestionBlockRepository questionBlockRepository;
    private final QuestionRepository questionRepository;
    private final ChapterMapper chapterMapper;

    @Override
    public ChapterResponse createDraftChapter(CreateDraftChapterRequest request, UUID userId) {
        ChapterEntity chapter = chapterMapper.toEntity(request);
        chapter.setId(UUID.randomUUID());
        chapter.setStatus(ChapterStatus.DRAFT);
        chapter.setCreatedBy(userId);
        chapter.setCreatedAt(LocalDateTime.now());

        chapter = chapterRepository.save(chapter);

        return chapterMapper.toResponse(chapter);
    }

    @Override
    public ChapterDetailResponse createPublishedChapter(CreatePublishedChapterRequest request, UUID userId) {
        if (request.getQuestionBlocks() == null || request.getQuestionBlocks().isEmpty()) {
            throw new BusinessValidationException("Cannot publish chapter without valid question blocks.");
        }

        ChapterEntity chapter = chapterMapper.toEntity(request);
        chapter.setId(UUID.randomUUID());
        chapter.setStatus(ChapterStatus.PUBLISHED);
        chapter.setCreatedBy(userId);
        chapter.setCreatedAt(LocalDateTime.now());

        chapter = chapterRepository.save(chapter);

        List<QuestionBlockEntity> savedBlocks = new ArrayList<>();
        int sortIndex = 0;
        
        for (QuestionBlockItemRequest blockReq : request.getQuestionBlocks()) {
            if (blockReq.getQuestionId() != null) {
                // Verify question exists
                questionRepository.findByIdAndDeletedAtIsNull(blockReq.getQuestionId())
                        .orElseThrow(() -> new BusinessValidationException("Question not found with ID: " + blockReq.getQuestionId()));
            }

            QuestionBlockEntity blockEntity = chapterMapper.toEntity(blockReq);
            blockEntity.setId(UUID.randomUUID());
            blockEntity.setChapterId(chapter.getId());
            blockEntity.setStatus(ChapterStatus.PUBLISHED);
            if (blockEntity.getSortOrder() == null) {
                blockEntity.setSortOrder(sortIndex++);
            }
            blockEntity.setCreatedBy(userId);
            blockEntity.setCreatedAt(LocalDateTime.now());
            
            savedBlocks.add(blockEntity);
        }
        
        savedBlocks = questionBlockRepository.saveAll(savedBlocks);

        ChapterDetailResponse response = chapterMapper.toDetailResponse(chapter);
        response.setQuestionBlocks(chapterMapper.toResponseList(savedBlocks));
        return response;
    }

    @Override
    public ChapterResponse updateChapter(UUID id, UpdateChapterRequest request, UUID userId) {
        ChapterEntity chapter = chapterRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chapter not found with ID: " + id));

        // Check if changing to PUBLISHED
        if (request.getStatus() == ChapterStatus.PUBLISHED && chapter.getStatus() == ChapterStatus.DRAFT) {
            long blockCount = questionBlockRepository.countByChapterIdAndDeletedAtIsNull(id);
            if (blockCount == 0) {
                throw new BusinessValidationException("Cannot publish chapter without at least 1 question block.");
            }
        }

        chapterMapper.updateEntity(chapter, request);
        chapter.setUpdatedBy(userId);
        chapter.setUpdatedAt(LocalDateTime.now());

        chapter = chapterRepository.save(chapter);

        return chapterMapper.toResponse(chapter);
    }

    @Override
    @Transactional(readOnly = true)
    public ChapterDetailResponse getChapterDetail(UUID id) {
        ChapterEntity chapter = chapterRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chapter not found with ID: " + id));

        List<QuestionBlockEntity> blocks = questionBlockRepository.findByChapterIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(id);

        ChapterDetailResponse response = chapterMapper.toDetailResponse(chapter);
        response.setQuestionBlocks(chapterMapper.toResponseList(blocks));
        
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChapterResponse> listChapters(String keyword, String status, UUID createdBy, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<ChapterEntity> chapterPage = chapterRepository.findAll(
                ChapterSpecification.filter(keyword, status, createdBy),
                pageable
        );

        Page<ChapterResponse> responsePage = chapterPage.map(chapterMapper::toResponse);
        return PageResponse.of(responsePage);
    }

    @Override
    public void deleteChapter(UUID id, UUID userId) {
        ChapterEntity chapter = chapterRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chapter not found with ID: " + id));

        if (chapter.getStatus() != ChapterStatus.DRAFT) {
            throw new BusinessValidationException("Only DRAFT chapters can be soft-deleted. Archive it instead.");
        }

        // Soft delete chapter
        chapterRepository.softDeleteChapter(id, userId);
        
        // Cascade soft delete to question blocks
        questionBlockRepository.softDeleteByChapterId(id, userId);
    }

    @Override
    public com.dts.content_builder.api.response.QuestionBlockResponse addQuestionBlock(UUID chapterId, com.dts.content_builder.api.form.CreateQuestionBlockRequest request, UUID userId) {
        ChapterEntity chapter = chapterRepository.findByIdAndDeletedAtIsNull(chapterId)
                .orElseThrow(() -> new ResourceNotFoundException("Chapter not found with ID: " + chapterId));

        if (request.getQuestionId() != null) {
            questionRepository.findByIdAndDeletedAtIsNull(request.getQuestionId())
                    .orElseThrow(() -> new BusinessValidationException("Question not found with ID: " + request.getQuestionId()));
        }

        int sortOrder = request.getSortOrder() != null ? request.getSortOrder() : 
                (int) questionBlockRepository.countByChapterIdAndDeletedAtIsNull(chapterId);

        QuestionBlockEntity blockEntity = QuestionBlockEntity.builder()
                .id(UUID.randomUUID())
                .chapterId(chapterId)
                .parentId(request.getParentId())
                .questionId(request.getQuestionId())
                .title(request.getTitle())
                .sortOrder(sortOrder)
                .status(request.getStatus() != null ? request.getStatus() : ChapterStatus.DRAFT)
                .metadata(request.getMetadata())
                .build();
        
        blockEntity.setCreatedBy(userId);
        blockEntity.setCreatedAt(java.time.LocalDateTime.now());

        blockEntity = questionBlockRepository.save(blockEntity);
        return chapterMapper.toResponse(blockEntity);
    }

    @Override
    public com.dts.content_builder.api.response.QuestionBlockResponse updateQuestionBlock(UUID chapterId, UUID blockId, com.dts.content_builder.api.form.UpdateQuestionBlockRequest request, UUID userId) {
        QuestionBlockEntity blockEntity = questionBlockRepository.findByIdAndChapterIdAndDeletedAtIsNull(blockId, chapterId)
                .orElseThrow(() -> new ResourceNotFoundException("Question Block not found with ID: " + blockId));

        if (request.getQuestionId() != null && !request.getQuestionId().equals(blockEntity.getQuestionId())) {
            questionRepository.findByIdAndDeletedAtIsNull(request.getQuestionId())
                    .orElseThrow(() -> new BusinessValidationException("Question not found with ID: " + request.getQuestionId()));
            blockEntity.setQuestionId(request.getQuestionId());
        }

        if (request.getTitle() != null) {
            blockEntity.setTitle(request.getTitle());
        }
        if (request.getStatus() != null) {
            blockEntity.setStatus(request.getStatus());
        }
        if (request.getMetadata() != null) {
            blockEntity.setMetadata(request.getMetadata());
        }

        blockEntity.setUpdatedBy(userId);
        blockEntity.setUpdatedAt(java.time.LocalDateTime.now());

        blockEntity = questionBlockRepository.save(blockEntity);
        return chapterMapper.toResponse(blockEntity);
    }

    @Override
    public void deleteQuestionBlock(UUID chapterId, UUID blockId, UUID userId) {
        QuestionBlockEntity blockEntity = questionBlockRepository.findByIdAndChapterIdAndDeletedAtIsNull(blockId, chapterId)
                .orElseThrow(() -> new ResourceNotFoundException("Question Block not found with ID: " + blockId));

        blockEntity.setDeletedAt(java.time.LocalDateTime.now());
        blockEntity.setUpdatedBy(userId);
        questionBlockRepository.save(blockEntity);
    }

    @Override
    public java.util.List<com.dts.content_builder.api.response.QuestionBlockResponse> reorderQuestionBlocks(UUID chapterId, java.util.List<com.dts.content_builder.api.form.ReorderItem> request, UUID userId) {
        java.util.List<QuestionBlockEntity> blocks = questionBlockRepository.findByChapterIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(chapterId);
        java.util.Map<UUID, QuestionBlockEntity> blockMap = blocks.stream().collect(java.util.stream.Collectors.toMap(QuestionBlockEntity::getId, b -> b));
        
        java.util.List<QuestionBlockEntity> updatedBlocks = new java.util.ArrayList<>();
        for (com.dts.content_builder.api.form.ReorderItem item : request) {
            QuestionBlockEntity block = blockMap.get(item.getId());
            if (block != null) {
                block.setSortOrder(item.getSortOrder());
                block.setUpdatedBy(userId);
                block.setUpdatedAt(java.time.LocalDateTime.now());
                updatedBlocks.add(block);
            }
        }
        questionBlockRepository.saveAll(updatedBlocks);
        
        blocks = questionBlockRepository.findByChapterIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(chapterId);
        return chapterMapper.toResponseList(blocks);
    }
}
