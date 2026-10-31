package com.dts.content_builder.api.form;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluateSimulationRequest {

    @NotNull(message = "Thời điểm chọn (selectedSecond) không được để trống")
    @DecimalMin(value = "0.0", message = "selectedSecond phải lớn hơn hoặc bằng 0")
    private Double selectedSecond;
}

