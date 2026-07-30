package com.dts.content_builder.domain.specification;

import com.dts.content_builder.application.enums.ChapterStatus;
import com.dts.content_builder.domain.entity.ChapterEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.UUID;

public class ChapterSpecification {

    public static Specification<ChapterEntity> filter(String keyword, String status, UUID createdBy) {
        return (root, query, criteriaBuilder) -> {
            Specification<ChapterEntity> spec = Specification.where(isNotDeleted());

            if (StringUtils.hasText(keyword)) {
                spec = spec.and((r, q, cb) -> cb.like(cb.lower(r.get("title")), "%" + keyword.toLowerCase() + "%"));
            }

            if (StringUtils.hasText(status)) {
                try {
                    ChapterStatus chapterStatus = ChapterStatus.valueOf(status.toUpperCase());
                    spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), chapterStatus));
                } catch (IllegalArgumentException e) {
                    // Ignore invalid status
                }
            }

            if (createdBy != null) {
                spec = spec.and((r, q, cb) -> cb.equal(r.get("createdBy"), createdBy));
            }

            return spec.toPredicate(root, query, criteriaBuilder);
        };
    }

    private static Specification<ChapterEntity> isNotDeleted() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.isNull(root.get("deletedAt"));
    }
}
