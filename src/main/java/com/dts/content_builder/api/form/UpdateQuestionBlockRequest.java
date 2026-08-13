package com.dts.content_builder.api.form;

import com.dts.content_builder.application.enums.ChapterStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class UpdateQuestionBlockRequest {
    private UUID questionId;

    @NotBlank
    @Size(max = 255)
    private String title;

    private ChapterStatus status;

    private Map<String, Object> metadata;
}
