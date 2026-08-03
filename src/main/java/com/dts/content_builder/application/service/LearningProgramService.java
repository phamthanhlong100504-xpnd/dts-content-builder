package com.dts.content_builder.application.service;

import com.dts.content_builder.api.response.LearningProgramResponse;
import com.dts.content_builder.api.response.PageResponse;
import com.dts.content_builder.application.enums.LearningProgramStatus;
import com.dts.content_builder.application.mapper.LearningProgramMapper;
import com.dts.content_builder.domain.entity.LearningProgramEntity;
import com.dts.content_builder.domain.repository.LearningProgramRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LearningProgramService {

    private final LearningProgramRepository learningProgramRepository;
    private final LearningProgramMapper learningProgramMapper;
    private final com.dts.content_builder.domain.repository.ChapterBlockRepository chapterBlockRepository;
    private final com.dts.content_builder.domain.repository.ChapterRepository chapterRepository;
    private final ChapterBlockService chapterBlockService;

    @Transactional(readOnly = true)
    public PageResponse<LearningProgramResponse> listLearningPrograms(
            String keyword, String code, String status, UUID createdBy, int page, int size, String sortParam) {

        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt"); // Default
        if (sortParam != null && !sortParam.isEmpty()) {
            String[] parts = sortParam.split(",");
            if (parts.length == 2) {
                sort = Sort.by(Sort.Direction.fromString(parts[1]), parts[0]);
            } else if (parts.length == 1) {
                sort = Sort.by(Sort.Direction.ASC, parts[0]);
            }
        }

        Pageable pageable = PageRequest.of(page, size, sort);

        Specification<LearningProgramEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (keyword != null && !keyword.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%"));
            }

            if (code != null && !code.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("code"), code));
            }

            if (status != null && !status.trim().isEmpty()) {
                try {
                    LearningProgramStatus enumStatus = LearningProgramStatus.valueOf(status.toUpperCase());
                    predicates.add(cb.equal(root.get("status"), enumStatus));
                } catch (IllegalArgumentException e) {
                    // Ignore invalid status filter
                }
            }

            if (createdBy != null) {
                predicates.add(cb.equal(root.get("createdBy"), createdBy));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<LearningProgramEntity> pageResult = learningProgramRepository.findAll(spec, pageable);

        List<LearningProgramResponse> content = pageResult.getContent().stream()
                .map(learningProgramMapper::toResponse)
                .collect(Collectors.toList());

        return new PageResponse<>(
                content,
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages(),
                pageResult.isLast()
        );
    }
    @Transactional
    public LearningProgramResponse createDraft(com.dts.content_builder.api.form.CreateLearningProgramRequest request, UUID userId) {
        if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
            if (learningProgramRepository.existsByCodeAndDeletedAtIsNull(request.getCode())) {
                throw new IllegalArgumentException("Learning program with this code already exists");
            }
        }

        LearningProgramEntity entity = new LearningProgramEntity();
        entity.setId(UUID.randomUUID());
        entity.setTitle(request.getTitle());
        entity.setCode(request.getCode());
        entity.setDescription(request.getDescription());
        entity.setStatus(LearningProgramStatus.DRAFT);
        entity.setMetadata(request.getMetadata() != null ? request.getMetadata() : new java.util.HashMap<>());
        entity.setCreatedBy(userId);
        entity.setCreatedAt(java.time.LocalDateTime.now());
        entity.setUpdatedAt(java.time.LocalDateTime.now());

        learningProgramRepository.save(entity);

        return learningProgramMapper.toResponse(entity);
    }

    @Transactional
    public LearningProgramResponse createPublished(com.dts.content_builder.api.form.CreatePublishedLearningProgramRequest request, UUID userId) {
        if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
            if (learningProgramRepository.existsByCodeAndDeletedAtIsNull(request.getCode())) {
                throw new IllegalArgumentException("Learning program with this code already exists");
            }
        }

        if (request.getChapterBlocks() == null || request.getChapterBlocks().isEmpty()) {
            throw new IllegalArgumentException("Cannot publish learning program without chapter blocks");
        }

        LearningProgramEntity entity = new LearningProgramEntity();
        entity.setId(UUID.randomUUID());
        entity.setTitle(request.getTitle());
        entity.setCode(request.getCode());
        entity.setDescription(request.getDescription());
        entity.setStatus(LearningProgramStatus.PUBLISHED);
        entity.setMetadata(request.getMetadata() != null ? request.getMetadata() : new java.util.HashMap<>());
        entity.setCreatedBy(userId);
        entity.setCreatedAt(java.time.LocalDateTime.now());
        entity.setUpdatedAt(java.time.LocalDateTime.now());

        learningProgramRepository.save(entity);

        List<com.dts.content_builder.domain.entity.ChapterBlockEntity> blocks = new ArrayList<>();
        for (com.dts.content_builder.api.form.ChapterBlockItem item : request.getChapterBlocks()) {
            if (item.getChapterId() != null) {
                chapterRepository.findByIdAndDeletedAtIsNull(item.getChapterId())
                        .orElseThrow(() -> new IllegalArgumentException("Chapter not found: " + item.getChapterId()));
            }

            com.dts.content_builder.domain.entity.ChapterBlockEntity block = new com.dts.content_builder.domain.entity.ChapterBlockEntity();
            block.setId(item.getId() != null ? item.getId() : UUID.randomUUID());
            block.setLearningProgramId(entity.getId());
            block.setParentId(item.getParentId());
            block.setChapterId(item.getChapterId());
            block.setTitle(item.getTitle());
            block.setSortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0);
            block.setStatus(com.dts.content_builder.application.enums.ChapterBlockStatus.PUBLISHED);
            block.setMetadata(item.getMetadata() != null ? item.getMetadata() : new java.util.HashMap<>());
            block.setCreatedBy(userId);
            block.setCreatedAt(java.time.LocalDateTime.now());
            block.setUpdatedAt(java.time.LocalDateTime.now());
            blocks.add(block);
        }

        chapterBlockRepository.saveAll(blocks);

        LearningProgramResponse response = learningProgramMapper.toResponse(entity);
        com.dts.content_builder.application.mapper.ChapterBlockMapper blockMapper = new com.dts.content_builder.application.mapper.ChapterBlockMapper();
        response.setChapterBlocks(blockMapper.toTreeResponse(blocks));

        return response;
    }

    @Transactional(readOnly = true)
    public LearningProgramResponse getDetail(UUID id, boolean includeChapterBlocks) {
        LearningProgramEntity entity = learningProgramRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Learning program not found"));

        LearningProgramResponse response = learningProgramMapper.toResponse(entity);

        if (includeChapterBlocks) {
            response.setChapterBlocks(chapterBlockService.getChapterBlockTree(id));
        }

        return response;
    }

    @Transactional
    public LearningProgramResponse update(UUID id, com.dts.content_builder.api.form.UpdateLearningProgramRequest request, UUID userId) {
        LearningProgramEntity entity = learningProgramRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Learning program not found"));

        if (!entity.getCreatedBy().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to modify this learning program.");
        }

        if (request.getCode() != null && !request.getCode().trim().isEmpty()) {
            if (learningProgramRepository.existsByCodeAndIdNotAndDeletedAtIsNull(request.getCode(), id)) {
                throw new IllegalArgumentException("Learning program with this code already exists");
            }
        }

        if (request.getStatus() == LearningProgramStatus.PUBLISHED && entity.getStatus() == LearningProgramStatus.DRAFT) {
            List<com.dts.content_builder.domain.entity.ChapterBlockEntity> blocks = chapterBlockRepository.findByLearningProgramIdAndDeletedAtIsNull(id);
            if (blocks.isEmpty()) {
                throw new IllegalArgumentException("Cannot publish learning program: it has no chapter blocks");
            }
        }

        entity.setTitle(request.getTitle());
        entity.setCode(request.getCode());
        entity.setDescription(request.getDescription());
        entity.setStatus(request.getStatus());
        if (request.getMetadata() != null) {
            entity.setMetadata(request.getMetadata());
        }
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(java.time.LocalDateTime.now());

        learningProgramRepository.save(entity);

        return learningProgramMapper.toResponse(entity);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        LearningProgramEntity entity = learningProgramRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Learning program not found"));

        if (!entity.getCreatedBy().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to modify this learning program.");
        }

        if (entity.getStatus() != LearningProgramStatus.DRAFT) {
            throw new IllegalArgumentException("Cannot delete learning program: Only DRAFT programs can be deleted.");
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        entity.setDeletedAt(now);
        entity.setUpdatedBy(userId);
        entity.setUpdatedAt(now);
        learningProgramRepository.save(entity);

        chapterBlockRepository.deleteByLearningProgramId(id, now, userId, now);
    }
}
