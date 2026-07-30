package com.dts.content_builder.application.service;

import com.dts.content_builder.api.form.CreateChapterBlockRequest;
import com.dts.content_builder.api.form.ReorderItem;
import com.dts.content_builder.api.form.UpdateChapterBlockRequest;
import com.dts.content_builder.api.response.ChapterBlockDetailResponse;
import com.dts.content_builder.api.response.ChapterBlockTreeResponse;
import com.dts.content_builder.application.enums.ChapterBlockStatus;
import com.dts.content_builder.application.mapper.ChapterBlockMapper;
import com.dts.content_builder.domain.entity.ChapterBlockEntity;
import com.dts.content_builder.domain.entity.LearningProgramEntity;
import com.dts.content_builder.domain.repository.ChapterBlockRepository;
import com.dts.content_builder.domain.repository.ChapterRepository;
import com.dts.content_builder.domain.repository.LearningProgramRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChapterBlockService {

    private final ChapterBlockRepository chapterBlockRepository;
    private final LearningProgramRepository learningProgramRepository;
    private final ChapterRepository chapterRepository;
    private final ChapterBlockMapper chapterBlockMapper;

    @Transactional(readOnly = true)
    public List<ChapterBlockTreeResponse> getChapterBlockTree(UUID learningProgramId) {
        if (learningProgramRepository.findByIdAndDeletedAtIsNull(learningProgramId).isEmpty()) {
            throw new EntityNotFoundException("Learning program not found");
        }

        List<ChapterBlockEntity> blocks = chapterBlockRepository.findByLearningProgramIdAndDeletedAtIsNull(learningProgramId);
        return chapterBlockMapper.toTreeResponse(blocks);
    }

    @Transactional
    public ChapterBlockDetailResponse addChapterBlock(UUID learningProgramId, CreateChapterBlockRequest request, UUID userId) {
        checkOwnership(learningProgramId, userId);

        validateParentAndChapter(learningProgramId, request.getParentId(), request.getChapterId());

        Integer sortOrder = request.getSortOrder();
        if (sortOrder == null) {
            Integer maxSortOrder = chapterBlockRepository.findMaxSortOrderByLearningProgramIdAndParentId(learningProgramId, request.getParentId());
            sortOrder = (maxSortOrder == null) ? 0 : maxSortOrder + 1;
        }

        ChapterBlockEntity entity = new ChapterBlockEntity();
        entity.setId(UUID.randomUUID());
        entity.setLearningProgramId(learningProgramId);
        entity.setParentId(request.getParentId());
        entity.setChapterId(request.getChapterId());
        entity.setTitle(request.getTitle());
        entity.setSortOrder(sortOrder);
        entity.setStatus(request.getStatus() != null ? request.getStatus() : ChapterBlockStatus.DRAFT);
        entity.setMetadata(request.getMetadata() != null ? request.getMetadata() : new HashMap<>());
        entity.setCreatedBy(userId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        chapterBlockRepository.save(entity);

        return chapterBlockMapper.toDetailResponse(entity);
    }

    @Transactional
    public ChapterBlockDetailResponse updateChapterBlock(UUID learningProgramId, UUID blockId, UpdateChapterBlockRequest request, UUID userId) {
        checkOwnership(learningProgramId, userId);

        ChapterBlockEntity entity = chapterBlockRepository.findByIdAndLearningProgramIdAndDeletedAtIsNull(blockId, learningProgramId)
                .orElseThrow(() -> new EntityNotFoundException("Chapter block not found"));

        validateParentAndChapter(learningProgramId, request.getParentId(), request.getChapterId());

        if (request.getParentId() != null) {
            checkCyclicDependency(learningProgramId, blockId, request.getParentId());
        }

        entity.setParentId(request.getParentId());
        entity.setChapterId(request.getChapterId());
        entity.setTitle(request.getTitle());
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        if (request.getMetadata() != null) {
            entity.setMetadata(request.getMetadata());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(userId);

        chapterBlockRepository.save(entity);

        return chapterBlockMapper.toDetailResponse(entity);
    }

    @Transactional
    public void deleteChapterBlock(UUID learningProgramId, UUID blockId, UUID userId) {
        checkOwnership(learningProgramId, userId);

        ChapterBlockEntity entity = chapterBlockRepository.findByIdAndLearningProgramIdAndDeletedAtIsNull(blockId, learningProgramId)
                .orElseThrow(() -> new EntityNotFoundException("Chapter block not found"));

        long childrenCount = chapterBlockRepository.countByParentIdAndDeletedAtIsNull(blockId);
        if (childrenCount > 0) {
            throw new IllegalArgumentException("Cannot delete chapter block that has children");
        }

        entity.setDeletedAt(LocalDateTime.now());
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(LocalDateTime.now());
        chapterBlockRepository.save(entity);
    }

    @Transactional
    public List<ChapterBlockDetailResponse> reorderChapterBlocks(UUID learningProgramId, List<ReorderItem> items, UUID userId) {
        checkOwnership(learningProgramId, userId);

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Items list cannot be empty");
        }

        Set<UUID> ids = items.stream().map(ReorderItem::getId).collect(Collectors.toSet());
        if (ids.size() != items.size()) {
            throw new IllegalArgumentException("Duplicate block IDs found in request");
        }

        Set<Integer> sortOrders = items.stream().map(ReorderItem::getSortOrder).collect(Collectors.toSet());
        if (sortOrders.size() != items.size()) {
            throw new IllegalArgumentException("Duplicate sort orders found in request");
        }

        List<ChapterBlockEntity> blocks = chapterBlockRepository.findAllById(ids);
        if (blocks.size() != items.size()) {
            throw new EntityNotFoundException("One or more chapter blocks not found");
        }

        UUID commonParentId = blocks.get(0).getParentId();
        for (ChapterBlockEntity block : blocks) {
            if (!block.getLearningProgramId().equals(learningProgramId) || block.getDeletedAt() != null) {
                throw new EntityNotFoundException("Block does not belong to learning program or is deleted");
            }
            if ((commonParentId == null && block.getParentId() != null) || 
                (commonParentId != null && !commonParentId.equals(block.getParentId()))) {
                throw new IllegalArgumentException("All blocks must share the same parentId");
            }
        }

        Map<UUID, Integer> orderMap = items.stream().collect(Collectors.toMap(ReorderItem::getId, ReorderItem::getSortOrder));

        for (ChapterBlockEntity block : blocks) {
            block.setSortOrder(orderMap.get(block.getId()));
            block.setUpdatedBy(userId);
            block.setUpdatedAt(LocalDateTime.now());
        }

        chapterBlockRepository.saveAll(blocks);

        return blocks.stream()
                .map(chapterBlockMapper::toDetailResponse)
                .collect(Collectors.toList());
    }

    private void checkOwnership(UUID learningProgramId, UUID userId) {
        LearningProgramEntity program = learningProgramRepository.findByIdAndDeletedAtIsNull(learningProgramId)
                .orElseThrow(() -> new EntityNotFoundException("Learning program not found"));

        if (!program.getCreatedBy().equals(userId)) {
            // Wait, normally an ADMIN can edit anything. We'll stick to strict SEC-006a for now as agreed.
            throw new AccessDeniedException("You do not have permission to modify this learning program.");
        }
    }

    private void validateParentAndChapter(UUID learningProgramId, UUID parentId, UUID chapterId) {
        if (parentId != null) {
            ChapterBlockEntity parent = chapterBlockRepository.findByIdAndLearningProgramIdAndDeletedAtIsNull(parentId, learningProgramId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent chapter block not found"));
        }
        if (chapterId != null) {
            chapterRepository.findByIdAndDeletedAtIsNull(chapterId)
                    .orElseThrow(() -> new IllegalArgumentException("Chapter not found"));
        }
    }

    private void checkCyclicDependency(UUID learningProgramId, UUID blockId, UUID newParentId) {
        if (blockId.equals(newParentId)) {
            throw new IllegalArgumentException("Chapter block cannot be its own parent");
        }
        
        UUID currentParentId = newParentId;
        while (currentParentId != null) {
            ChapterBlockEntity parent = chapterBlockRepository.findByIdAndLearningProgramIdAndDeletedAtIsNull(currentParentId, learningProgramId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent block not found in hierarchy"));
            
            if (blockId.equals(parent.getId())) {
                throw new IllegalArgumentException("Cyclic dependency detected");
            }
            currentParentId = parent.getParentId();
        }
    }
}
