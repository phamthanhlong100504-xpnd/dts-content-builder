package com.dts.content_builder.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InternalQuestionMetadataResponse {

    // Existing fields remain compatible with current consumers.
    private UUID id;
    private List<UUID> optionIds;

    // A question may be linked to several chapters in the selected source.
    private List<UUID> chapterIds;

    // When a license class is supplied, use its imported critical-question rule.
    private Boolean isCritical;

    private String status;
    private String questionType;

    // IDs resolved through the existing Media service.
    private List<String> mediaFileIds;

    // Deterministic revision of the source question and its options.
    private String contentVersion;

    // Retain the previous constructor for source compatibility.
    public InternalQuestionMetadataResponse(UUID id, List<UUID> optionIds) {
        this.id = id;
        this.optionIds = optionIds;
    }
}
