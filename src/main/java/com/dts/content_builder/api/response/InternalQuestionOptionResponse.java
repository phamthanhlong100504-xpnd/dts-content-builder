package com.dts.content_builder.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalQuestionOptionResponse {
    private UUID id;
    private String content;
    private Integer sortOrder;
    private Boolean isCorrect;
}
