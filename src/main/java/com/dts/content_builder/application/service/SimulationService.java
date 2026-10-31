package com.dts.content_builder.application.service;

import com.dts.content_builder.api.form.EvaluateSimulationRequest;
import com.dts.content_builder.api.response.SimulationDetailResponse;
import com.dts.content_builder.api.response.SimulationEvaluationResponse;
import com.dts.content_builder.application.enums.QuestionType;
import com.dts.content_builder.application.exception.BusinessValidationException;
import com.dts.content_builder.application.exception.ResourceNotFoundException;
import com.dts.content_builder.domain.entity.QuestionEntity;
import com.dts.content_builder.domain.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SimulationService {

    private final QuestionRepository questionRepository;

    public SimulationDetailResponse getSimulationDetail(String idOrNumber) {
        QuestionEntity question = findSimulationQuestion(idOrNumber);
        return mapToDetailResponse(question);
    }

    public SimulationEvaluationResponse evaluateSimulation(String idOrNumber, EvaluateSimulationRequest request) {
        QuestionEntity question = findSimulationQuestion(idOrNumber);
        Double selectedSecond = request.getSelectedSecond();
        if (selectedSecond == null || selectedSecond < 0) {
            throw new BusinessValidationException("Thời điểm bấm phím (selectedSecond) phải lớn hơn hoặc bằng 0");
        }

        Map<String, Object> metadata = question.getMetadata() != null ? question.getMetadata() : Collections.emptyMap();
        Map<String, Object> explanations = question.getExplanations() != null ? question.getExplanations() : Collections.emptyMap();

        @SuppressWarnings("unchecked")
        Map<String, Object> timelineObj = (Map<String, Object>) metadata.get("scoringTimeline");
        if (timelineObj == null) {
            throw new BusinessValidationException("Tình huống mô phỏng thiếu cấu hình scoringTimeline để chấm điểm");
        }

        double t5Start = parseTimelineVal(timelineObj.get("t5_start"));
        double t4Start = parseTimelineVal(timelineObj.get("t4_start"));
        double t3Start = parseTimelineVal(timelineObj.get("t3_start"));
        double t2Start = parseTimelineVal(timelineObj.get("t2_start"));
        double t1Start = parseTimelineVal(timelineObj.get("t1_start"));
        double t0End = parseTimelineVal(timelineObj.get("t0_end"));

        int score;
        String evaluationResult;
        String errorCode = null;
        String message;

        if (selectedSecond < t5Start) {
            score = 0;
            evaluationResult = "PRESSED_TOO_EARLY";
            errorCode = "SIMULATION_PRESSED_TOO_EARLY";
            message = String.format("Bạn đã bấm quá sớm tại giây %.2fs (Tình huống nguy hiểm chưa xuất hiện, mốc 5 điểm bắt đầu lúc %.2fs). Điểm đạt được: 0 điểm.",
                    selectedSecond, t5Start);
        } else if (selectedSecond < t4Start) {
            score = 5;
            evaluationResult = "PERFECT";
            message = String.format("Xuất sắc! Bạn đã bấm tại giây %.2fs - thời điểm vàng đạt điểm tối đa: 5/5 điểm (Khung 5 điểm: %.2fs - %.2fs).",
                    selectedSecond, t5Start, t4Start);
        } else if (selectedSecond < t3Start) {
            score = 4;
            evaluationResult = "GOOD";
            message = String.format("Rất tốt! Bạn đã bấm tại giây %.2fs - phản xạ an toàn. Đạt: 4/5 điểm (Khung 4 điểm: %.2fs - %.2fs).",
                    selectedSecond, t4Start, t3Start);
        } else if (selectedSecond < t2Start) {
            score = 3;
            evaluationResult = "ACCEPTABLE";
            message = String.format("Đạt: 3/5 điểm tại giây %.2fs (Khung 3 điểm: %.2fs - %.2fs). Bạn cần quan sát và phản xạ sớm hơn.",
                    selectedSecond, t3Start, t2Start);
        } else if (selectedSecond < t1Start) {
            score = 2;
            evaluationResult = "LATE";
            message = String.format("Đạt: 2/5 điểm tại giây %.2fs (Khung 2 điểm: %.2fs - %.2fs). Phản xạ còn chậm, mức độ an toàn thấp.",
                    selectedSecond, t2Start, t1Start);
        } else if (selectedSecond <= t0End) {
            score = 1;
            evaluationResult = "VERY_LATE";
            message = String.format("Đạt: 1/5 điểm tại giây %.2fs (Khung 1 điểm: %.2fs - %.2fs). Tình huống cận kề tai nạn giao thông.",
                    selectedSecond, t1Start, t0End);
        } else {
            score = 0;
            evaluationResult = "PRESSED_TOO_LATE";
            errorCode = "SIMULATION_PRESSED_TOO_LATE";
            message = String.format("Bạn đã bấm quá muộn tại giây %.2fs (Tai nạn/va chạm đã xảy ra sau mốc %.2fs). Điểm đạt được: 0 điểm.",
                    selectedSecond, t0End);
        }

        Integer scenarioNum = metadata.get("scenarioNumber") instanceof Number
                ? ((Number) metadata.get("scenarioNumber")).intValue() : null;
        String tip = explanations.get("tip") != null ? explanations.get("tip").toString() : "";
        String hazardSign = explanations.get("hazardSign") != null ? explanations.get("hazardSign").toString() : "";

        return SimulationEvaluationResponse.builder()
                .questionId(question.getId())
                .scenarioNumber(scenarioNum)
                .title(question.getContent())
                .selectedSecond(selectedSecond)
                .score(score)
                .evaluationResult(evaluationResult)
                .errorCode(errorCode)
                .message(message)
                .tip(tip)
                .hazardSign(hazardSign)
                .scoringTimeline(timelineObj)
                .build();
    }

    private QuestionEntity findSimulationQuestion(String idOrNumber) {
        if (idOrNumber == null || idOrNumber.trim().isEmpty()) {
            throw new BusinessValidationException("Định danh tình huống không được để trống");
        }
        String cleanId = idOrNumber.trim();

        // 1. Kiểm tra nếu là UUID
        try {
            UUID uuid = UUID.fromString(cleanId);
            return questionRepository.findByIdAndDeletedAtIsNull(uuid)
                    .filter(q -> q.getType() == QuestionType.SIMULATION)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy câu hỏi mô phỏng với ID: " + cleanId));
        } catch (IllegalArgumentException notUuid) {
            // 2. Không phải UUID -> tra cứu theo scenarioNumber (1-120)
            return questionRepository.findSimulationByScenarioNumber(cleanId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tình huống mô phỏng số: " + cleanId));
        }
    }

    private SimulationDetailResponse mapToDetailResponse(QuestionEntity question) {
        Map<String, Object> metadata = question.getMetadata() != null ? question.getMetadata() : Collections.emptyMap();
        Map<String, Object> explanations = question.getExplanations() != null ? question.getExplanations() : Collections.emptyMap();

        Integer scenarioNumber = metadata.get("scenarioNumber") instanceof Number
                ? ((Number) metadata.get("scenarioNumber")).intValue() : null;
        Integer chapterId = metadata.get("chapterId") instanceof Number
                ? ((Number) metadata.get("chapterId")).intValue() : null;
        String chapterName = metadata.get("chapterName") != null ? metadata.get("chapterName").toString() : null;
        Double duration = metadata.get("durationSeconds") instanceof Number
                ? ((Number) metadata.get("durationSeconds")).doubleValue() : null;

        @SuppressWarnings("unchecked")
        Map<String, Object> scoringTimeline = (Map<String, Object>) metadata.get("scoringTimeline");

        List<String> mediaFileIds = question.getMediaFileIds() != null ? question.getMediaFileIds() : Collections.emptyList();
        String videoMediaId = mediaFileIds.size() > 0 ? mediaFileIds.get(0) : null;
        String thumbnailMediaId = mediaFileIds.size() > 1 ? mediaFileIds.get(1) : null;

        String tip = explanations.get("tip") != null ? explanations.get("tip").toString() : "";
        String hazardSign = explanations.get("hazardSign") != null ? explanations.get("hazardSign").toString() : "";

        return SimulationDetailResponse.builder()
                .id(question.getId())
                .scenarioNumber(scenarioNumber)
                .title(question.getContent())
                .chapterId(chapterId)
                .chapterName(chapterName)
                .durationSeconds(duration)
                .videoMediaId(videoMediaId)
                .thumbnailMediaId(thumbnailMediaId)
                .mediaFileIds(mediaFileIds)
                .tip(tip)
                .hazardSign(hazardSign)
                .scoringTimeline(scoringTimeline)
                .status(question.getStatus() != null ? question.getStatus().name() : null)
                .build();
    }

    private double parseTimelineVal(Object val) {
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        if (val != null) {
            return Double.parseDouble(val.toString());
        }
        return 0.0;
    }
}

