package com.dts.content_builder.api.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class CreatePublishedChapterRequest {

    @NotBlank(message = "Title must not be blank")
    @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    private String title;

    private Map<String, Object> metadata;

    @Valid
    @NotEmpty(message = "Question blocks are required for published chapter")
    @Size(min = 1, max = 200, message = "Must contain 1 to 200 question blocks")
    private List<QuestionBlockItemRequest> questionBlocks;
}
