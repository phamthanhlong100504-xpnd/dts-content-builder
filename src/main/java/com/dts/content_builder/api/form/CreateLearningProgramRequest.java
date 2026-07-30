package com.dts.content_builder.api.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class CreateLearningProgramRequest {
    @NotBlank
    @Size(max = 255)
    private String title;

    @Size(max = 100)
    private String code;

    private String description;

    private Map<String, Object> metadata;
}
