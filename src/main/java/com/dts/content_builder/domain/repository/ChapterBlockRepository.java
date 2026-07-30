package com.dts.content_builder.domain.repository;

import com.dts.content_builder.domain.entity.ChapterBlockEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChapterBlockRepository extends JpaRepository<ChapterBlockEntity, UUID> {

    List<ChapterBlockEntity> findByLearningProgramIdAndDeletedAtIsNull(UUID learningProgramId);

    Optional<ChapterBlockEntity> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT MAX(cb.sortOrder) FROM ChapterBlockEntity cb WHERE cb.learningProgramId = :learningProgramId AND (cb.parentId = :parentId OR (:parentId IS NULL AND cb.parentId IS NULL)) AND cb.deletedAt IS NULL")
    Integer findMaxSortOrderByLearningProgramIdAndParentId(
            @Param("learningProgramId") UUID learningProgramId,
            @Param("parentId") UUID parentId
    );

    long countByParentIdAndDeletedAtIsNull(UUID parentId);
    
    Optional<ChapterBlockEntity> findByIdAndLearningProgramIdAndDeletedAtIsNull(UUID id, UUID learningProgramId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE ChapterBlockEntity cb SET cb.deletedAt = :deletedAt, cb.updatedBy = :updatedBy, cb.updatedAt = :updatedAt WHERE cb.learningProgramId = :learningProgramId AND cb.deletedAt IS NULL")
    int deleteByLearningProgramId(
            @Param("learningProgramId") UUID learningProgramId,
            @Param("deletedAt") java.time.LocalDateTime deletedAt,
            @Param("updatedBy") UUID updatedBy,
            @Param("updatedAt") java.time.LocalDateTime updatedAt
    );
}
