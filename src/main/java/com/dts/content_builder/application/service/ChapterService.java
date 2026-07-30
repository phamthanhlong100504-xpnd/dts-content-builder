package com.dts.content_builder.application.service;

import com.dts.content_builder.api.form.CreateDraftChapterRequest;
import com.dts.content_builder.api.form.CreatePublishedChapterRequest;
import com.dts.content_builder.api.form.UpdateChapterRequest;
import com.dts.content_builder.api.response.ChapterDetailResponse;
import com.dts.content_builder.api.response.ChapterResponse;
import com.dts.content_builder.api.response.PageResponse;

import java.util.UUID;

public interface ChapterService {

    ChapterResponse createDraftChapter(CreateDraftChapterRequest request, UUID userId);

    ChapterDetailResponse createPublishedChapter(CreatePublishedChapterRequest request, UUID userId);

    ChapterResponse updateChapter(UUID id, UpdateChapterRequest request, UUID userId);

    ChapterDetailResponse getChapterDetail(UUID id);

    PageResponse<ChapterResponse> listChapters(String keyword, String status, UUID createdBy, int page, int size);

    void deleteChapter(UUID id, UUID userId);
}
