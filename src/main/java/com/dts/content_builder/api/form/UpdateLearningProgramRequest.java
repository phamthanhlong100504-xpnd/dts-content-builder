package com.dts.content_builder.api.form;

import com.dts.content_builder.application.enums.LearningProgramStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateLearningProgramRequest {
    @NotBlank
    @Size(max = 255)
    private String title;

    @Size(max = 100)
    private String code;

    private String description;

    @NotNull
    private LearningProgramStatus status;

    private Map<String, Object> metadata;
}
