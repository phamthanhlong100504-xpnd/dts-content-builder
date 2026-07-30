package com.dts.content_builder.api.form;

import com.dts.content_builder.application.enums.QuestionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddQuestionOptionRequest {

    @NotBlank(message = "Content must not be blank")
    @Size(max = 10000, message = "Content must not exceed 10000 characters")
    private String content;

    private Integer sortOrder;

    @NotNull(message = "isCorrect flag is required")
    private Boolean isCorrect;

    @Builder.Default
    private QuestionStatus status = QuestionStatus.DRAFT;

    private Map<String, Object> metadata;
}
