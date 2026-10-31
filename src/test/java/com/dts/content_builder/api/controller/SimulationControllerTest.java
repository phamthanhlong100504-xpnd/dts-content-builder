package com.dts.content_builder.api.controller;

import com.dts.content_builder.api.form.EvaluateSimulationRequest;
import com.dts.content_builder.api.response.SimulationDetailResponse;
import com.dts.content_builder.api.response.SimulationEvaluationResponse;
import com.dts.content_builder.application.exception.ResourceNotFoundException;
import com.dts.content_builder.application.service.SimulationService;
import com.dts.content_builder.config.JwtProvider;
import com.dts.content_builder.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SimulationController.class)
@Import(SecurityConfig.class)
public class SimulationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SimulationService simulationService;

    @MockBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("GET /simulations/{idOrNumber} - Thành công khi lấy thông tin tình huống")
    public void testGetSimulationDetail_Success() throws Exception {
        UUID questionId = UUID.randomUUID();
        SimulationDetailResponse response = SimulationDetailResponse.builder()
                .id(questionId)
                .scenarioNumber(1)
                .title("Người đi bộ sang đường bị khuất sau xe tải")
                .chapterId(1)
                .chapterName("Giao thông trên đường đô thị")
                .durationSeconds(27.0)
                .videoMediaId("3c18f45d-3981-4a20-91d5-5fb339375f48")
                .thumbnailMediaId("0cce6c10-def0-4873-a179-046080d84aee")
                .mediaFileIds(List.of("3c18f45d-3981-4a20-91d5-5fb339375f48", "0cce6c10-def0-4873-a179-046080d84aee"))
                .tip("Nhấn space khi quan sát rõ thấy người đi bộ thứ nhất")
                .hazardSign("Người đi bộ sau xe tải sang đường")
                .scoringTimeline(Map.of("t5_start", 17.18, "t0_end", 19.85))
                .status("PUBLISHED")
                .build();

        when(simulationService.getSimulationDetail("1")).thenReturn(response);

        mockMvc.perform(get("/api/v1/content-builder/simulations/1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(questionId.toString()))
                .andExpect(jsonPath("$.scenarioNumber").value(1))
                .andExpect(jsonPath("$.title").value("Người đi bộ sang đường bị khuất sau xe tải"))
                .andExpect(jsonPath("$.chapterId").value(1))
                .andExpect(jsonPath("$.durationSeconds").value(27.0))
                .andExpect(jsonPath("$.videoMediaId").value("3c18f45d-3981-4a20-91d5-5fb339375f48"));
    }

    @Test
    @DisplayName("GET /simulations/{idOrNumber} - Trả về 404 khi không tìm thấy tình huống")
    public void testGetSimulationDetail_NotFound() throws Exception {
        when(simulationService.getSimulationDetail("999"))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy tình huống mô phỏng số: 999"));

        mockMvc.perform(get("/api/v1/content-builder/simulations/999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /simulations/{idOrNumber}/evaluate - Bấm sớm trả về 0 điểm và lỗi PRESSED_TOO_EARLY")
    public void testEvaluateSimulation_PressedTooEarly() throws Exception {
        UUID questionId = UUID.randomUUID();
        EvaluateSimulationRequest req = new EvaluateSimulationRequest(15.0);

        SimulationEvaluationResponse res = SimulationEvaluationResponse.builder()
                .questionId(questionId)
                .scenarioNumber(1)
                .title("Người đi bộ sang đường bị khuất sau xe tải")
                .selectedSecond(15.0)
                .score(0)
                .evaluationResult("PRESSED_TOO_EARLY")
                .errorCode("SIMULATION_PRESSED_TOO_EARLY")
                .message("Bạn đã bấm quá sớm tại giây 15.00s")
                .scoringTimeline(Map.of("t5_start", 17.18, "t0_end", 19.85))
                .build();

        when(simulationService.evaluateSimulation(eq("1"), any(EvaluateSimulationRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/v1/content-builder/simulations/1/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.evaluationResult").value("PRESSED_TOO_EARLY"))
                .andExpect(jsonPath("$.errorCode").value("SIMULATION_PRESSED_TOO_EARLY"));
    }

    @Test
    @DisplayName("POST /simulations/{idOrNumber}/evaluate - Bấm chuẩn thời điểm vàng trả về 5 điểm PERFECT")
    public void testEvaluateSimulation_Perfect() throws Exception {
        UUID questionId = UUID.randomUUID();
        EvaluateSimulationRequest req = new EvaluateSimulationRequest(17.5);

        SimulationEvaluationResponse res = SimulationEvaluationResponse.builder()
                .questionId(questionId)
                .scenarioNumber(1)
                .title("Người đi bộ sang đường bị khuất sau xe tải")
                .selectedSecond(17.5)
                .score(5)
                .evaluationResult("PERFECT")
                .errorCode(null)
                .message("Xuất sắc! Bạn đã bấm tại giây 17.50s")
                .scoringTimeline(Map.of("t5_start", 17.18, "t0_end", 19.85))
                .build();

        when(simulationService.evaluateSimulation(eq("1"), any(EvaluateSimulationRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/v1/content-builder/simulations/1/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(5))
                .andExpect(jsonPath("$.evaluationResult").value("PERFECT"))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    @DisplayName("POST /simulations/{idOrNumber}/evaluate - Bấm muộn sau tai nạn trả về 0 điểm PRESSED_TOO_LATE")
    public void testEvaluateSimulation_PressedTooLate() throws Exception {
        UUID questionId = UUID.randomUUID();
        EvaluateSimulationRequest req = new EvaluateSimulationRequest(21.0);

        SimulationEvaluationResponse res = SimulationEvaluationResponse.builder()
                .questionId(questionId)
                .scenarioNumber(1)
                .title("Người đi bộ sang đường bị khuất sau xe tải")
                .selectedSecond(21.0)
                .score(0)
                .evaluationResult("PRESSED_TOO_LATE")
                .errorCode("SIMULATION_PRESSED_TOO_LATE")
                .message("Bạn đã bấm quá muộn tại giây 21.00s")
                .scoringTimeline(Map.of("t5_start", 17.18, "t0_end", 19.85))
                .build();

        when(simulationService.evaluateSimulation(eq("1"), any(EvaluateSimulationRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/v1/content-builder/simulations/1/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.evaluationResult").value("PRESSED_TOO_LATE"))
                .andExpect(jsonPath("$.errorCode").value("SIMULATION_PRESSED_TOO_LATE"));
    }

    @Test
    @DisplayName("POST /simulations/{idOrNumber}/evaluate - Báo lỗi 400 Bad Request khi truyền selectedSecond âm")
    public void testEvaluateSimulation_InvalidBody() throws Exception {
        EvaluateSimulationRequest req = new EvaluateSimulationRequest(-1.0);

        mockMvc.perform(post("/api/v1/content-builder/simulations/1/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}

