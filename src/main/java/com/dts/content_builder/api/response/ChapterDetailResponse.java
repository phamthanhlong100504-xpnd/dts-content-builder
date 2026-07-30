package com.dts.content_builder.api.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChapterDetailResponse extends ChapterResponse {

    private List<QuestionBlockResponse> questionBlocks;
}
