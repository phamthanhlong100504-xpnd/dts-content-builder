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
}
