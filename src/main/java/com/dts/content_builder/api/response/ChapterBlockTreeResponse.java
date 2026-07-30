package com.dts.content_builder.api.response;

import com.dts.content_builder.application.enums.ChapterBlockStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class ChapterBlockTreeResponse {
    private UUID id;
    private UUID learningProgramId;
    private UUID parentId;
    private UUID chapterId;
    private String title;
    private Integer sortOrder;
    private ChapterBlockStatus status;
    private Map<String, Object> metadata;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<ChapterBlockTreeResponse> children;
}
