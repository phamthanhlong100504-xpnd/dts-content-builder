package com.dts.content_builder.api.response;

import com.dts.content_builder.application.enums.ChapterStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionBlockResponse {

    private UUID id;
    private UUID chapterId;
    private UUID questionId;
    private UUID parentId;
    private String title;
    private Integer sortOrder;
    private ChapterStatus status;
    private Map<String, Object> metadata;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private UUID updatedBy;
    private LocalDateTime updatedAt;
}
