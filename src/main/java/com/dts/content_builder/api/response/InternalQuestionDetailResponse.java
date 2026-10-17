package com.dts.content_builder.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalQuestionDetailResponse {

    // Existing fields remain compatible with current consumers.
    private UUID id;
    private String content;
    private String type;
    private Boolean isCritical;
    private List<String> mediaFileIds;
    private List<InternalQuestionOptionResponse> options;

    private String status;

    // Must match the revision supplied by question metadata.
    private String contentVersion;

    // Read from QuestionEntity.explanations without generating content.
    // This internal data is released to students only under the exam rule.
    private Map<String, Object> explanations;

    // Retain the previous constructor for source compatibility.
    public InternalQuestionDetailResponse(
            UUID id,
            String content,
            String type,
            Boolean isCritical,
            List<String> mediaFileIds,
            List<InternalQuestionOptionResponse> options) {
        this.id = id;
        this.content = content;
        this.type = type;
        this.isCritical = isCritical;
        this.mediaFileIds = mediaFileIds;
        this.options = options;
    }
}
