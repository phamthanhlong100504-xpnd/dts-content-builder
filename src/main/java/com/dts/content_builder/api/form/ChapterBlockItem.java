package com.dts.content_builder.api.form;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class ChapterBlockItem {
    private UUID id;
    private UUID parentId;
    private UUID chapterId;

    @NotBlank
    @Size(max = 255)
    private String title;

    @Min(0)
    private Integer sortOrder;

    private Map<String, Object> metadata;
}
