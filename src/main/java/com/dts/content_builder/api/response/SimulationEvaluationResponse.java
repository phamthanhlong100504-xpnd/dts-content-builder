package com.dts.content_builder.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimulationEvaluationResponse {

    private UUID questionId;
    private Integer scenarioNumber;
    private String title;
    private Double selectedSecond;
    private Integer score;
    private String evaluationResult;
    private String errorCode;
    private String message;
    private String tip;
    private String hazardSign;
    private Map<String, Object> scoringTimeline;
}

