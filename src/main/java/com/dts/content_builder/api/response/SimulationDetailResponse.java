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
public class SimulationDetailResponse {

    private UUID id;
    private Integer scenarioNumber;
    private String title;
    private Integer chapterId;
    private String chapterName;
    private Double durationSeconds;
    private String videoMediaId;
    private String thumbnailMediaId;
    private List<String> mediaFileIds;
    private String tip;
    private String hazardSign;
    private Map<String, Object> scoringTimeline;
    private String status;
}

