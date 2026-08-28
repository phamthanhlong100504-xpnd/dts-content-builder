package com.dts.content_builder.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalQuestionDetailResponse {
    private UUID id;
    private String content;
    private String type;
    private Boolean isCritical;
    private List<String> mediaFileIds;
    private List<InternalQuestionOptionResponse> options;
}
