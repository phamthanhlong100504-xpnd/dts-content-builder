package com.dts.content_builder.domain.repository;

import com.dts.content_builder.domain.entity.QuestionOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuestionOptionRepository extends JpaRepository<QuestionOptionEntity, UUID> {
    List<QuestionOptionEntity> findByQuestionId(UUID questionId);

    Optional<QuestionOptionEntity> findByIdAndQuestionIdAndDeletedAtIsNull(UUID id, UUID questionId);

    List<QuestionOptionEntity> findByIdInAndQuestionIdAndDeletedAtIsNull(List<UUID> ids, UUID questionId);

    @Query("SELECT COALESCE(MAX(o.sortOrder), -1) FROM QuestionOptionEntity o WHERE o.questionId = :questionId AND o.deletedAt IS NULL")
    Integer findMaxSortOrderByQuestionId(@Param("questionId") UUID questionId);

    List<QuestionOptionEntity> findByQuestionIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(UUID questionId);

    @Modifying
    @Query("UPDATE QuestionOptionEntity o SET o.deletedAt = CURRENT_TIMESTAMP, o.updatedBy = :userId WHERE o.questionId = :questionId AND o.deletedAt IS NULL")
    void softDeleteOptionsByQuestionId(@Param("questionId") UUID questionId, @Param("userId") UUID userId);
}
