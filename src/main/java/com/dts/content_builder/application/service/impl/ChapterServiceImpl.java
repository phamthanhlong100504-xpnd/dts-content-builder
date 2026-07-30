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
}
