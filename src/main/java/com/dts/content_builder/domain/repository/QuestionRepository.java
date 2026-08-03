package com.dts.content_builder.domain.repository;

import com.dts.content_builder.domain.entity.QuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<QuestionEntity, UUID>, JpaSpecificationExecutor<QuestionEntity> {
    Optional<QuestionEntity> findByIdAndDeletedAtIsNull(UUID id);
    
    List<QuestionEntity> findByIdInAndDeletedAtIsNull(List<UUID> ids);

    @Modifying
    @Query("UPDATE QuestionEntity q SET q.deletedAt = CURRENT_TIMESTAMP, q.updatedBy = :userId WHERE q.id = :id AND q.deletedAt IS NULL")
    void softDeleteQuestion(@Param("id") UUID id, @Param("userId") UUID userId);
}
