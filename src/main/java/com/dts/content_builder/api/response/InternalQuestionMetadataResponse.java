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
    private UUID id;
    private List<UUID> optionIds;
}
