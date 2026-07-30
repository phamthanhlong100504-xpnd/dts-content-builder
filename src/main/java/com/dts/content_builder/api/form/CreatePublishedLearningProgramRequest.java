package com.dts.content_builder.api.form;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class CreatePublishedLearningProgramRequest extends CreateLearningProgramRequest {

    @NotEmpty
    @Size(max = 500)
    @Valid
    private List<ChapterBlockItem> chapterBlocks;
}
