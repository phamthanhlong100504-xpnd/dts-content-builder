package com.dts.content_builder.domain.repository;

import com.dts.content_builder.domain.entity.QuestionOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionOptionRepository extends JpaRepository<QuestionOptionEntity, UUID> {
    List<QuestionOptionEntity> findByQuestionId(UUID questionId);
}
