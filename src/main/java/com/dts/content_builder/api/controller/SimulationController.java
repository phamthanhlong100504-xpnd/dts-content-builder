package com.dts.content_builder.api.controller;

import com.dts.content_builder.api.form.EvaluateSimulationRequest;
import com.dts.content_builder.api.response.SimulationDetailResponse;
import com.dts.content_builder.api.response.SimulationEvaluationResponse;
import com.dts.content_builder.application.service.SimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content-builder/simulations")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    /**
     * Lấy thông tin chi tiết của một tình huống mô phỏng theo UUID hoặc số thứ tự kịch bản (1 - 120).
     */
    @GetMapping("/{idOrNumber}")
    public SimulationDetailResponse getSimulationDetail(@PathVariable String idOrNumber) {
        return simulationService.getSimulationDetail(idOrNumber);
    }

    /**
     * Chấm điểm và đánh giá phản xạ khi học viên nhấn phím Space tại một thời điểm (giây).
     * Trả về điểm số (0 - 5), phân loại kết quả, nhận xét/lỗi (bấm quá sớm, quá muộn) và timeline chi tiết.
     */
    @PostMapping("/{idOrNumber}/evaluate")
    @ResponseStatus(HttpStatus.OK)
    public SimulationEvaluationResponse evaluateSimulation(
            @PathVariable String idOrNumber,
            @Valid @RequestBody EvaluateSimulationRequest request) {
        return simulationService.evaluateSimulation(idOrNumber, request);
    }
}

