package com.dts.content_builder.api.response;

import com.dts.content_builder.application.enums.QuestionStatus;
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionOptionResponse {
    private UUID id;
    private String content;
    private Integer sortOrder;
    private Boolean isCorrect;
    private QuestionStatus status;
    private Map<String, Object> metadata;
}
