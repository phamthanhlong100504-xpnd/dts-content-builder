package com.dts.content_builder.api.response;

import com.dts.content_builder.application.enums.QuestionStatus;
import com.dts.content_builder.application.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponse {
    private UUID id;
    private QuestionType type;
    private String content;
    private Map<String, Object> explanations;
    private List<String> mediaFileIds;
    private Map<String, Object> attachments;
    private Map<String, Object> references;
    private QuestionStatus status;
    private Map<String, Object> metadata;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private List<QuestionOptionResponse> options;
}
