package com.dts.content_builder.api.response;

import com.dts.content_builder.application.enums.LearningProgramStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
public class LearningProgramResponse {
    private UUID id;
    private String title;
    private String code;
    private String description;
    private LearningProgramStatus status;
    private Map<String, Object> metadata;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private java.util.List<ChapterBlockTreeResponse> chapterBlocks;
}
