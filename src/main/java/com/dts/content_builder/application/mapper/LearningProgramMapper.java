package com.dts.content_builder.application.mapper;

import com.dts.content_builder.api.response.LearningProgramResponse;
import com.dts.content_builder.domain.entity.LearningProgramEntity;
import org.springframework.stereotype.Component;

@Component
public class LearningProgramMapper {

    public LearningProgramResponse toResponse(LearningProgramEntity entity) {
        if (entity == null) {
            return null;
        }

        LearningProgramResponse response = new LearningProgramResponse();
        response.setId(entity.getId());
        response.setTitle(entity.getTitle());
        response.setCode(entity.getCode());
        response.setDescription(entity.getDescription());
        response.setStatus(entity.getStatus());
        response.setMetadata(entity.getMetadata());
        response.setCreatedBy(entity.getCreatedBy());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());

        return response;
    }
}
