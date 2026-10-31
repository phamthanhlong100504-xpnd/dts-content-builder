package com.dts.content_builder.api.response;

import java.util.List;
import java.util.UUID;

/** Published source bank for the authenticated internal examination reader. */
public record InternalQuestionBankResponse(String bankVersion, String licenseClass,
        List<ChapterReference> chapters, List<InternalQuestionMetadataResponse> questions) {
    public record ChapterReference(UUID id, int sourceChapterId, String bankVersion) {
    }
}
