package com.dts.content_builder.api.form;

import com.dts.content_builder.application.enums.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateQuestionRequest {

    @NotNull(message = "Type is required")
    private QuestionType type;

    @NotBlank(message = "Content must not be blank")
    @Size(min = 1, max = 50000, message = "Content must be between 1 and 50000 characters")
    private String content;

    private Map<String, Object> explanations;

    @Size(max = 20, message = "Maximum 20 media file IDs allowed")
    private List<String> mediaFileIds;

    private Map<String, Object> attachments;

    private Map<String, Object> references;

    private Map<String, Object> metadata;

    @Valid
    @Size(max = 50, message = "Maximum 50 options allowed")
    private List<QuestionOptionItemRequest> options;
}
