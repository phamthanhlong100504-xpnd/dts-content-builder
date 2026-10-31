
package com.dts.content_builder.application.service;

import com.dts.content_builder.api.form.CreateMemoryTipRequest;
import com.dts.content_builder.api.response.MemoryTipResponse;
import com.dts.content_builder.api.response.PageResponse;
import com.dts.content_builder.application.exception.BusinessValidationException;
import com.dts.content_builder.application.exception.ResourceNotFoundException;
import com.dts.content_builder.application.mapper.MemoryTipMapper;
import com.dts.content_builder.domain.entity.MemoryTipEntity;
import com.dts.content_builder.domain.repository.MemoryTipRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class MemoryTipService {

    private final MemoryTipRepository memoryTipRepository;
    private final MemoryTipMapper memoryTipMapper;

    // Tao meo ghi nho o trang thai DRAFT
    public MemoryTipResponse createDraft(
            CreateMemoryTipRequest request,
            UUID userId
    ) {
        return create(
                request,
                userId,
                MemoryTipEntity.Status.DRAFT
        );
    }

    // Tao va xuat ban meo ghi nho
    public MemoryTipResponse createPublished(
            CreateMemoryTipRequest request,
            UUID userId
    ) {
        return create(
                request,
                userId,
                MemoryTipEntity.Status.PUBLISHED
        );
    }

    private MemoryTipResponse create(
            CreateMemoryTipRequest request,
            UUID userId,
            MemoryTipEntity.Status status
    ) {
        if (request == null) {
            throw new BusinessValidationException(
                    "Memory tip request is required."
            );
        }

        if (request.getBankVersion() == null
                || request.getBankVersion().isBlank()) {
            throw new BusinessValidationException(
                    "Bank version is required."
            );
        }

        if (request.getSourceId() == null
                || request.getSourceId() < 1) {
            throw new BusinessValidationException(
                    "Source ID must be positive."
            );
        }

        if (request.getSortOrder() == null
                || request.getSortOrder() < 1) {
            throw new BusinessValidationException(
                    "Sort order must be positive."
            );
        }

        if (request.getTitle() == null
                || request.getTitle().isBlank()) {
            throw new BusinessValidationException(
                    "Memory tip title is required."
            );
        }

        if (request.getQuickRule() == null
                || request.getQuickRule().isBlank()) {
            throw new BusinessValidationException(
                    "Quick rule is required."
            );
        }

        if (request.getExplanation() == null
                || request.getExplanation().isBlank()) {
            throw new BusinessValidationException(
                    "Explanation is required."
            );
        }

        // Khong cho trung ID nguon trong cung phien ban
        boolean sourceExists = memoryTipRepository
                .findBySourceIdAndBankVersionAndDeletedAtIsNull(
                        request.getSourceId(),
                        request.getBankVersion()
                )
                .isPresent();

        if (sourceExists) {
            throw new BusinessValidationException(
                    "Memory tip source ID already exists "
                            + "in this bank version."
            );
        }

        MemoryTipEntity entity = memoryTipMapper.toEntity(
                request,
                userId,
                status
        );

        if (status == MemoryTipEntity.Status.PUBLISHED) {
            entity.setPublishedAt(LocalDateTime.now());
        }

        MemoryTipEntity saved = memoryTipRepository.save(
                entity
        );

        return memoryTipMapper.toResponse(saved);
    }

    // Danh sach meo da PUBLISHED
    // Ho tro phien ban va tim kiem theo tieu de
    @Transactional(readOnly = true)
    public PageResponse<MemoryTipResponse> listPublished(
            String bankVersion,
            String keyword,
            Pageable pageable
    ) {
        if (bankVersion == null || bankVersion.isBlank()) {
            throw new BusinessValidationException(
                    "Bank version is required."
            );
        }

        Specification<MemoryTipEntity> specification =
                (root, query, cb) -> {

                    var predicate = cb.and(
                            cb.equal(
                                    root.get("bankVersion"),
                                    bankVersion
                            ),
                            cb.equal(
                                    root.get("status"),
                                    MemoryTipEntity.Status.PUBLISHED
                            ),
                            cb.isNull(
                                    root.get("deletedAt")
                            )
                    );

                    if (keyword != null
                            && !keyword.isBlank()) {

                        String search = "%"
                                + keyword.trim().toLowerCase()
                                + "%";

                        predicate = cb.and(
                                predicate,
                                cb.like(
                                        cb.lower(root.get("title")),
                                        search
                                )
                        );
                    }

                    return predicate;
                };

        Page<MemoryTipResponse> page = memoryTipRepository
                .findAll(specification, pageable)
                .map(memoryTipMapper::toResponse);

        return PageResponse.of(page);
    }

    // Lay chi tiet mot meo da xuat ban
    @Transactional(readOnly = true)
    public MemoryTipResponse getPublishedById(UUID id) {

        MemoryTipEntity entity = memoryTipRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Memory tip not found: " + id
                        )
                );

        if (entity.getStatus()
                != MemoryTipEntity.Status.PUBLISHED) {
            throw new ResourceNotFoundException(
                    "Memory tip not found: " + id
            );
        }

        return memoryTipMapper.toResponse(entity);
    }
}
