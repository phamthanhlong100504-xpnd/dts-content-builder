package com.dts.content_builder.domain.repository;

import com.dts.content_builder.domain.entity.ChapterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChapterRepository extends JpaRepository<ChapterEntity, UUID>, JpaSpecificationExecutor<ChapterEntity> {
    Optional<ChapterEntity> findByIdAndDeletedAtIsNull(UUID id);

    @Modifying
    @Query("UPDATE ChapterEntity c SET c.deletedAt = CURRENT_TIMESTAMP, c.updatedBy = :userId WHERE c.id = :id AND c.deletedAt IS NULL")
    void softDeleteChapter(@Param("id") UUID id, @Param("userId") UUID userId);
}
