package com.dts.content_builder.domain.repository;

import com.dts.content_builder.domain.entity.QuestionBlockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuestionBlockRepository extends JpaRepository<QuestionBlockEntity, UUID> {
    List<QuestionBlockEntity> findByChapterIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(UUID chapterId);
    
    List<QuestionBlockEntity> findByChapterIdInAndDeletedAtIsNull(List<UUID> chapterIds);

    Optional<QuestionBlockEntity> findByIdAndChapterIdAndDeletedAtIsNull(UUID id, UUID chapterId);

    @Modifying
    @Query("UPDATE QuestionBlockEntity qb SET qb.deletedAt = CURRENT_TIMESTAMP, qb.updatedBy = :userId WHERE qb.chapterId = :chapterId AND qb.deletedAt IS NULL")
    void softDeleteByChapterId(@Param("chapterId") UUID chapterId, @Param("userId") UUID userId);

    @Query("SELECT COUNT(qb) FROM QuestionBlockEntity qb WHERE qb.chapterId = :chapterId AND qb.deletedAt IS NULL")
    long countByChapterIdAndDeletedAtIsNull(@Param("chapterId") UUID chapterId);

    boolean existsByQuestionIdAndDeletedAtIsNull(UUID questionId);
}
