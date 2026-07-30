package com.dts.content_builder.domain.specification;

import com.dts.content_builder.application.enums.QuestionStatus;
import com.dts.content_builder.application.enums.QuestionType;
import com.dts.content_builder.domain.entity.QuestionEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QuestionSpecification {

    public static Specification<QuestionEntity> filterQuestions(
            String keyword,
            QuestionType type,
            QuestionStatus status,
            UUID createdBy) {

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Always exclude soft-deleted records
            predicates.add(criteriaBuilder.isNull(root.get("deletedAt")));

            if (StringUtils.hasText(keyword)) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("content")),
                        "%" + keyword.toLowerCase() + "%"
                ));
            }

            if (type != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), type));
            }

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            if (createdBy != null) {
                predicates.add(criteriaBuilder.equal(root.get("createdBy"), createdBy));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
