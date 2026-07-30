package com.dts.content_builder.domain.repository;

import com.dts.content_builder.domain.entity.LearningProgramEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LearningProgramRepository extends JpaRepository<LearningProgramEntity, UUID>, JpaSpecificationExecutor<LearningProgramEntity> {
    
    java.util.Optional<LearningProgramEntity> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndIdNotAndDeletedAtIsNull(String code, UUID id);
}
