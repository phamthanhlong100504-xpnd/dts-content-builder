
package com.dts.content_builder.application.service;

import com.dts.content_builder.api.form.CreateTrafficSignRequest;
import com.dts.content_builder.api.response.PageResponse;
import com.dts.content_builder.api.response.TrafficSignResponse;
import com.dts.content_builder.application.exception.BusinessValidationException;
import com.dts.content_builder.application.exception.ResourceNotFoundException;
import com.dts.content_builder.application.mapper.TrafficSignMapper;
import com.dts.content_builder.domain.entity.TrafficSignEntity;
import com.dts.content_builder.domain.repository.TrafficSignRepository;

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
public class TrafficSignService {

    private final TrafficSignRepository trafficSignRepository;
    private final TrafficSignMapper trafficSignMapper;

    // Tao bien bao o trang thai DRAFT
    public TrafficSignResponse createDraft(
            CreateTrafficSignRequest request,
            UUID userId
    ) {
        return create(
                request,
                userId,
                TrafficSignEntity.Status.DRAFT
        );
    }

    // Tao va xuat ban bien bao
    public TrafficSignResponse createPublished(
            CreateTrafficSignRequest request,
            UUID userId
    ) {
        return create(
                request,
                userId,
                TrafficSignEntity.Status.PUBLISHED
        );
    }

    private TrafficSignResponse create(
            CreateTrafficSignRequest request,
            UUID userId,
            TrafficSignEntity.Status status
    ) {
        if (request == null) {
            throw new BusinessValidationException(
                    "Traffic sign request is required."
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

        if (request.getCode() == null
                || request.getCode().isBlank()) {
            throw new BusinessValidationException(
                    "Traffic sign code is required."
            );
        }

        // Khong cho trung ID nguon trong cung phien ban
        boolean sourceExists = trafficSignRepository
                .findBySourceIdAndBankVersionAndDeletedAtIsNull(
                        request.getSourceId(),
                        request.getBankVersion()
                )
                .isPresent();

        if (sourceExists) {
            throw new BusinessValidationException(
                    "Traffic sign source ID already exists "
                            + "in this bank version."
            );
        }

        // Khong cho trung ma bien trong cung phien ban
        boolean codeExists = trafficSignRepository
                .findByCodeIgnoreCaseAndBankVersionAndDeletedAtIsNull(
                        request.getCode(),
                        request.getBankVersion()
                )
                .isPresent();

        if (codeExists) {
            throw new BusinessValidationException(
                    "Traffic sign code already exists "
                            + "in this bank version."
            );
        }

        // Bien bao PUBLISHED phai duoc gan Media UUID
        if (status == TrafficSignEntity.Status.PUBLISHED
                && request.getMediaFileId() == null) {
            throw new BusinessValidationException(
                    "Published traffic sign requires mediaFileId."
            );
        }

        TrafficSignEntity entity = trafficSignMapper.toEntity(
                request,
                userId,
                status
        );

        if (status == TrafficSignEntity.Status.PUBLISHED) {
            entity.setPublishedAt(LocalDateTime.now());
        }

        TrafficSignEntity saved = trafficSignRepository.save(
                entity
        );

        return trafficSignMapper.toResponse(saved);
    }

    // Lay danh sach bien bao da PUBLISHED
    // Ho tro loc theo phien ban, nhom va tu khoa
    @Transactional(readOnly = true)
    public PageResponse<TrafficSignResponse> listPublished(
            String bankVersion,
            String category,
            String keyword,
            Pageable pageable
    ) {
        if (bankVersion == null || bankVersion.isBlank()) {
            throw new BusinessValidationException(
                    "Bank version is required."
            );
        }

        Specification<TrafficSignEntity> specification =
                (root, query, cb) -> {

                    var predicate = cb.and(
                            cb.equal(
                                    root.get("bankVersion"),
                                    bankVersion
                            ),
                            cb.equal(
                                    root.get("status"),
                                    TrafficSignEntity.Status.PUBLISHED
                            ),
                            cb.isNull(
                                    root.get("deletedAt")
                            )
                    );

                    if (category != null
                            && !category.isBlank()) {
                        predicate = cb.and(
                                predicate,
                                cb.equal(
                                        root.get("category"),
                                        category.trim()
                                )
                        );
                    }

                    if (keyword != null
                            && !keyword.isBlank()) {

                        String search = "%"
                                + keyword.trim().toLowerCase()
                                + "%";

                        predicate = cb.and(
                                predicate,
                                cb.or(
                                        cb.like(
                                                cb.lower(
                                                        root.get("code")
                                                ),
                                                search
                                        ),
                                        cb.like(
                                                cb.lower(
                                                        root.get("name")
                                                ),
                                                search
                                        )
                                )
                        );
                    }

                    return predicate;
                };

        Page<TrafficSignResponse> page =
                trafficSignRepository
                        .findAll(specification, pageable)
                        .map(trafficSignMapper::toResponse);

        return PageResponse.of(page);
    }

    // Chi tiet bien bao da xuat ban
    @Transactional(readOnly = true)
    public TrafficSignResponse getPublishedById(UUID id) {

        TrafficSignEntity entity = trafficSignRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Traffic sign not found: " + id
                        )
                );

        if (entity.getStatus()
                != TrafficSignEntity.Status.PUBLISHED) {
            throw new ResourceNotFoundException(
                    "Traffic sign not found: " + id
            );
        }

        return trafficSignMapper.toResponse(entity);
    }
}
